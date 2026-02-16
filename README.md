# FlashDB - Distributed Key-Value Store

A distributed, persistent key-value store built in Java 21, implementing the architecture from the project design document. Component-wise development covering LSM Tree, networking, distribution, and compaction.

**Real-world demo:** Uses the [Titanic dataset](https://github.com/datasciencedojo/datasets) (open source) to demonstrate FlashDB as a passenger record cache for incident response dashboards.

## Components (per Design Document)

| Phase | Component | Implementation |
|-------|-----------|----------------|
| **1** | Local Storage Engine (LSM Tree) | MemTable, WAL, SSTable, Sparse Index, Read-Write Lock |
| **2** | Network Server | Binary protocol, Netty, Virtual Thread executor |
| **3** | Distributed Layer | Consistent Hash Ring, replication, request routing |
| **4** | Compaction | Background merge of SSTables (merge-sort) |

## Tech Stack

- **Java 21** (Virtual Threads)
- **Netty** - Non-blocking I/O
- **Protocol Buffers** - Message serialization
- **JUnit 5** - Testing
- **JMH** - Benchmarking

## Quick Start

> **Detailed step-by-step guide:** See [EXECUTION_STEPS.md](EXECUTION_STEPS.md)

### Build

```bash
mvn clean compile
```

### Run Server

```bash
mvn exec:java -Dexec.mainClass="com.flashdb.FlashDBMain"
# Or with args: mvn exec:java -Dexec.mainClass="com.flashdb.FlashDBMain" -Dexec.args="8080 ./data"
```

### Run Client Demo

**Start the server first** (in a separate terminal), then run the client:

```bash
# Terminal 1 - Start server
mvn exec:java

# Terminal 2 - Run client demo
mvn exec:java -Pclient
```

### Run Real Data Demo (Titanic Dataset)

Loads real passenger data from [DataScience Dojo / GitHub](https://github.com/datasciencedojo/datasets) and demonstrates FlashDB as a passenger record cache:

```bash
# Terminal 1 - Start server
mvn exec:java

# Terminal 2 - Run real data demo
mvn exec:java -Preal-data
```

**Use case:** Fast key-value lookups for incident response / disaster recovery dashboards. Falls back to bundled sample if offline.

### Run Distributed Cluster (3 nodes)

```bash
# Terminal 1
mvn exec:java -Dexec.args="8080 ./data1"

# Terminal 2
mvn exec:java -Dexec.args="8081 ./data2"

# Terminal 3
mvn exec:java -Dexec.args="8082 ./data3"
```

### Run Tests

```bash
mvn test
```

## Project Structure (Component-wise)

```
flashdb/
├── src/main/java/com/flashdb/
│   ├── storage/          # Phase 1: MemTable, WAL, SSTable, SparseIndex, StorageEngine
│   ├── protocol/         # Phase 2: Binary wire protocol
│   ├── server/           # Phase 2: Netty server + handler
│   ├── client/           # Client SDK (knows which server holds which key)
│   ├── distributed/     # Phase 3: HashRing, DistributedKVClient
│   ├── compaction/      # Phase 4: CompactionTask, SSTableStreamIterator
│   └── FlashDBMain.java
├── src/main/proto/       # Protobuf definitions
└── src/test/
```

## Resume Bullet Points

- *"Implemented an LSM-Tree Storage Engine from scratch in Java, utilizing MemTables (SkipList) and SSTables for high-throughput write operations."*
- *"Engineered a custom binary wire protocol using Netty, achieving low-latency non-blocking network communication."*
- *"Designed a Distributed Hash Ring with virtual nodes to ensure even data distribution and minimal reshuffling during node scaling."*
- *"Built a multi-threaded compaction engine to merge on-disk data segments in the background without blocking active client reads."*

## License

MIT
