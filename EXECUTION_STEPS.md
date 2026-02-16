# FlashDB – Execution Steps

## Prerequisites

- **Java 21** (or later)
- **Maven 3.6+**
- Two terminal windows/tabs

---

## Step 1: Navigate to Project

cd /project/path/

## Step 2: Build the Project

```bash
mvn clean compile
```

**Expected:** `BUILD SUCCESS`

---

## Step 3: Run Tests (Optional)

```bash
mvn test
```

**Expected:** `Tests run: 7, Failures: 0, Errors: 0`

---

## Step 4: Start the Server

**If you get "Address already in use":** Kill the process first:
```bash
kill $(lsof -t -i :8080) 2>/dev/null
```

**Terminal 1:**

```bash
mvn exec:java
```

**Expected:** `FlashDB server listening on port 8080`

**Important:** Keep this terminal running. The server must be running before any client commands.

---

## Step 5: Run the Client Demo

**Terminal 2** (new terminal, same directory):

```bash
cd 
mvn exec:java -Pclient
```

**Expected output:**
```
GET hello: world
GET foo: bar
GET foo after del: null
Demo complete.
```

---

## Step 6: Run Real Data Demo (Titanic Dataset)

**Server MUST be running** (Step 4). If you get TimeoutException, the server is not running.

**Terminal 2:**

```bash
mvn exec:java -Preal-data
```

**Expected:** Load progress, sample queries (passenger lookup), survival stats, update/delete demo.

**Data source:** https://github.com/datasciencedojo/datasets (Titanic CSV)

---

## Step 7: Run Distributed Cluster (3 Nodes)

**Terminal 1:**
```bash
mvn exec:java -Dexec.args="8080 ./data1"
```

**Terminal 2:**
```bash
mvn exec:java -Dexec.args="8081 ./data2"
```

**Terminal 3:**
```bash
mvn exec:java -Dexec.args="8082 ./data3"
```

**Expected:** Each server prints `FlashDB server listening on port 8080` (or 8081, 8082).

---

## Step 8: Stop the Server

In the terminal where the server is running, press:

```
Ctrl + C
```

---

## Quick Reference

| Command | Purpose |
|---------|---------|
| `mvn clean compile` | Build and compile |
| `mvn test` | Run tests |
| `mvn exec:java` | Start server (port 8080) |
| `mvn exec:java -Pclient` | Run client demo |
| `mvn exec:java -Preal-data` | Run real data demo (Titanic) |
| `mvn exec:java -Dexec.args="8080 ./data1"` | Start server on custom port |

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `Address already in use` | Port 8080 is taken. Run `lsof -i :8080` then `kill <PID>`, or use `-Dexec.args="8081 ./data"` |
| `ClassNotFoundException` | Run `mvn compile` before `exec:java` |
| `Connection refused` on client | Start the server first (Step 4) |
| `TimeoutException` on client | Start server in Terminal 1 first, then run client in Terminal 2 |
