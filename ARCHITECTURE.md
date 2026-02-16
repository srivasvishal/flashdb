# FlashDB: Low-Level Architecture & Implementation Details

## What FlashDB Does (High-Level)

FlashDB is a **distributed key-value store**—like a simplified Redis or Cassandra. You can:
- **PUT** key → value (store data)
- **GET** key → value (retrieve data)
- **DEL** key (delete data)

Data persists to disk, survives restarts, and can be distributed across multiple servers.

---

## 1. The LSM Tree: Why It Exists

**Problem:** Traditional databases do random disk writes (update a row in place). Disk random writes are **~1000x slower** than sequential writes.

**Solution:** Log-Structured Merge Tree (LSM) turns **random writes into sequential writes**:
1. All writes go to an in-memory buffer first (fast)
2. When full, the buffer is flushed to disk as one big sequential write (fast)
3. Reads may need to check memory + multiple disk files

---

## 2. Data Flow: What Happens on a PUT

```
Client sends PUT("user:1001", "Alice")
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  NETTY (Event Loop)                                              │
│  - Receives TCP bytes                                            │
│  - LengthFieldBasedFrameDecoder strips 4-byte length prefix       │
│  - Passes raw payload to KVServerHandler                         │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  BINARY PROTOCOL PARSING                                         │
│  Packet: [OpCode=1][KeyLen=8][Key="user:1001"][ValLen=5][Val]   │
│  - 1 byte: operation (1=PUT, 2=GET, 3=DEL)                      │
│  - 4 bytes: key length (big-endian int)                          │
│  - N bytes: key UTF-8                                             │
│  - 4 bytes: value length                                         │
│  - M bytes: value (raw bytes)                                    │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  VIRTUAL THREAD EXECUTOR (Java 21)                               │
│  - Offloads DB work so Netty event loop stays non-blocking       │
│  - Each request runs in its own virtual thread (lightweight)     │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  STORAGE ENGINE (Write Path)                                      │
│  1. Acquire WRITE LOCK (ReentrantReadWriteLock)                   │
│  2. If MemTable >= 64MB → switchMemTable() [flush to disk]        │
│  3. Append to WAL: FileChannel.position(size); channel.write()   │
│  4. channel.force(true) → fsync for durability                   │
│  5. memTable.put(key, value) → ConcurrentSkipListMap.put()       │
│  6. Release WRITE LOCK                                            │
└─────────────────────────────────────────────────────────────────┘
```

**Key low-level details:**
- **WAL first:** Data is written to `wal.log` before MemTable so a crash doesn't lose committed writes
- **MemTable:** `ConcurrentSkipListMap` keeps keys sorted; O(log n) insert, thread-safe
- **No random disk I/O on write:** Only append to WAL (sequential)

---

## 3. Data Flow: What Happens on a GET

```
Client sends GET("user:1001")
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  STORAGE ENGINE (Read Path)                                       │
│  1. Acquire READ LOCK (allows concurrent reads, blocks writes)    │
│  2. memTable.get("user:1001") → O(log n) tree lookup              │
│     - If found: return value (or null if tombstone)               │
│  3. If not in MemTable: iterate SSTables NEWEST → OLDEST          │
│     - Each SSTable: use SparseIndex to get byte range             │
│     - FileChannel.position(offset), read chunk, binary search      │
│     - Format: [keyLen][key][valLen][value] repeated                │
│  4. Release READ LOCK                                              │
└─────────────────────────────────────────────────────────────────┘
```

**Why check newest SSTable first?** LSM semantics: newer data overwrites older. First match wins.

---

## 4. MemTable: In-Memory Structure

```
ConcurrentSkipListMap<String, byte[]>
         │
         │  Internally: Skip List (probabilistic balanced tree)
         │  - Multiple levels of linked lists for O(log n) operations
         │  - Keys sorted lexicographically
         │  - Thread-safe: no external locking needed
         │
         ▼
  "order:001" → [0x7B 0x22 0x69 0x64 0x22 ...]  (JSON bytes)
  "order:002" → [0x7B 0x22 0x69 0x64 0x22 ...]
  "user:1001" → [0x7B 0x22 0x6E 0x61 0x6D 0x65 ...]
  ...
```

**Size limit:** 64 MB. When exceeded, `switchMemTable()`:
1. Snapshot the map (copy)
2. Clear MemTable
3. Rotate WAL (move wal.log → wal-{timestamp}.log)
4. Flush snapshot to `data-{timestamp}.sst` (sequential write)
5. Add new SSTable to list

---

## 5. WAL (Write-Ahead Log): Durability

```
wal.log (append-only file)
┌──────────────────────────────────────────────────────────────────┐
│ [OP=1][keyLen=8]["user:1001"][valLen=25]["{"name":"Alice"}"]     │
│ [OP=1][keyLen=9]["order:001"][valLen=120]["{...}"]               │
│ [OP=2][keyLen=8]["user:1001"]  ← OP=2 means DELETE               │
└──────────────────────────────────────────────────────────────────┘
```

**On startup:** `wal.replay(memTable)` reads the file sequentially, replays each PUT/DEL into MemTable. Restores state after crash.

**FileChannel:** Uses `position(size)` before each write to append. `force(true)` flushes OS buffer to disk.

---

## 6. SSTable: On-Disk Format

```
data-1739654321000.sst
┌─────────────────────────────────────────────────────────────────┐
│ [4 bytes: entry count = 10000]                                    │
│ [4][key bytes][4][value bytes]  ← entry 0                        │
│ [4][key bytes][4][value bytes]  ← entry 1                        │
│ ...                                                               │
│ [4][key bytes][4][value bytes]  ← entry 9999                      │
└─────────────────────────────────────────────────────────────────┘

Keys are SORTED. Enables binary search.
```

**Sparse Index:** In-memory map of every 100th key → file offset.
- Key "order:0500" → search index → offset 245760
- Seek to offset, scan forward until key found or passed
- Avoids reading entire 100MB file for one key

---

## 7. Tombstones: How DEL Works

LSM can't "delete" from an SSTable (immutable). Instead:
- **DEL("user:1001")** → PUT("user:1001", TOMBSTONE) where TOMBSTONE = `[0x00]`
- WAL records the delete
- On GET: if value is tombstone, return null
- On compaction: tombstones are dropped (old value never returned)

---

## 8. Networking: Netty Pipeline

```
Inbound (Client → Server):
  Socket bytes
    → LengthFieldBasedFrameDecoder (read 4-byte length, then N bytes)
    → KVServerHandler (parse BinaryProtocol, call StorageEngine)
    → Virtual Thread Executor (offload blocking I/O)

Outbound (Server → Client):
  Response bytes
    → LengthFieldPrepender (add 4-byte length prefix)
    → Socket
```

**Why length prefix?** TCP is a stream. Without framing, multiple messages could merge. Length prefix tells the receiver how many bytes to read for one message.

---

## 9. Consistent Hashing (Distributed Mode)

```
Hash Ring (0 to 2^20)
     │
     │  "user:1001" → MD5 hash → 0x3A2F1
     │  "order:0042" → MD5 hash → 0x8B901
     │
     ▼
  ┌─────┐     ┌─────┐     ┌─────┐
  │ N1  │────▶│ N2  │────▶│ N3  │────▶ back to N1
  │:8080│     │:8081│     │:8082│
  └─────┘     └─────┘     └─────┘
       ▲
       │
  Key hashes to 0x3A2F1 → first node clockwise = N2
```

**Virtual nodes:** Each physical server gets 150 points on the ring. Reduces hot spots when one server holds too many keys.

---

## 10. Compaction: Background Merge

**Problem:** Many small SSTables → slow reads (must check each file).

**Solution:** Background thread every 60 seconds:
1. Select 4 oldest SSTables
2. Merge-sort iterate (priority queue over 4 iterators)
3. Write merged SSTable (drop tombstones, dedupe by key)
4. Delete old 4 files, add new 1
5. Reads now check fewer, larger files

---

## Summary: Request Lifecycle

| Operation | Memory | Disk (WAL) | Disk (SSTable) |
|-----------|--------|------------|----------------|
| PUT | MemTable.put() | Append to wal.log | On flush only |
| GET | MemTable.get() or SSTable scan | — | Sparse index + seek |
| DEL | MemTable.put(tombstone) | Append delete record | Tombstone in SSTable until compaction |
