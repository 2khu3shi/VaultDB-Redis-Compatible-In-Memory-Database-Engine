# VaultDB– Redis-Compatible In-Memory Database Engine

VaultDB is a Redis-compatible in-memory database engine written in Java 21. It accepts TCP connections, speaks RESP, stores multiple data types in memory, supports TTL expiration, and can persist commands to an AOF log for replay after restart.

## What It Does

VaultDB behaves like a small Redis server for learning and portfolio use. You can connect with `redis-cli` or any RESP client and run commands such as `PING`, `SET`, `GET`, `LPUSH`, `HSET`, `SADD`, `TTL`, and `FLUSHALL`.

## Features

- RESP protocol over TCP with a custom parser and writer
- 200-thread fixed worker pool for client handling
- `ReentrantReadWriteLock` for parallel reads and safe writes
- In-memory support for strings, lists, hashes, and sets
- Dual TTL expiry: lazy eviction plus background sweeping
- Optional AOF persistence with replay on startup
- JUnit 5 test coverage for commands, TTL, concurrency, TCP, and persistence

## Project Layout

- [src/main/java/com/vaultdb/VaultDBServer.java](src/main/java/com/vaultdb/VaultDBServer.java): TCP server entry point
- [src/main/java/com/vaultdb/VaultDBEngine.java](src/main/java/com/vaultdb/VaultDBEngine.java): core data store and locking
- [src/main/java/com/vaultdb/commands/CommandHandler.java](src/main/java/com/vaultdb/commands/CommandHandler.java): Redis command dispatch
- [src/main/java/com/vaultdb/resp](src/main/java/com/vaultdb/resp): RESP parser and writer
- [src/main/java/com/vaultdb/persistence](src/main/java/com/vaultdb/persistence): AOF write and replay
- [src/main/java/com/vaultdb/ttl](src/main/java/com/vaultdb/ttl): TTL storage and sweeper
- [src/test/java/com/vaultdb](src/test/java/com/vaultdb): JUnit 5 tests

## Repository Tree

```text
.
├── build.bat
├── build.ps1
├── pom.xml
├── README.md
├── scripts/
│   └── redis-cli-test.ps1
├── src/
│   ├── main/
│   │   └── java/com/vaultdb/
│   │       ├── VaultDBEngine.java
│   │       ├── VaultDBServer.java
│   │       ├── commands/CommandHandler.java
│   │       ├── data/
│   │       ├── persistence/
│   │       ├── resp/
│   │       └── ttl/
│   └── test/
│       └── java/com/vaultdb/
│           ├── ConcurrencyTest.java
│           ├── HashAndSetCommandTest.java
│           ├── ListCommandTest.java
│           ├── MiscCommandTest.java
│           ├── RespAndPersistenceTest.java
│           ├── StringCommandTest.java
│           ├── TcpIntegrationTest.java
│           ├── TestSupport.java
│           └── TtlCommandTest.java
└── target/
	├── classes/
	└── test-classes/
```

## Run It

### Quick Start

If you just want to run the project right away from a compiled checkout:

```powershell
java -cp target/classes com.vaultdb.VaultDBServer --no-persistence --port 6379
```

If you want to rebuild it from source and verify everything:

```powershell
mvn clean test
mvn package
java -jar target/vaultdb-1.0.0.jar
```

On Windows, the helper scripts are:

```powershell
.\build.ps1
.\build.ps1 package
```

### What To Do After Starting

Once the server is running on port 6379, open another terminal and try:

```powershell
$client = [System.Net.Sockets.TcpClient]::new('127.0.0.1', 6379)
$stream = $client.GetStream()
$payload = "*1`r`n`$4`r`nPING`r`n"
$bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
$stream.Write($bytes, 0, $bytes.Length)
$stream.Flush()
$buffer = New-Object byte[] 1024
$read = $stream.Read($buffer, 0, $buffer.Length)
[System.Text.Encoding]::UTF8.GetString($buffer, 0, $read)
$client.Close()
```

Expected response: `+PONG`

### Redis CLI Example

If you have `redis-cli`, this works too:

```bash
redis-cli -p 6379 ping
redis-cli -p 6379 set user:1 ankit
redis-cli -p 6379 get user:1
```

## Try It With redis-cli

```bash
redis-cli -p 6379 ping
redis-cli -p 6379 set user:1 ankit
redis-cli -p 6379 get user:1
redis-cli -p 6379 set session abc EX 30
redis-cli -p 6379 lpush queue job1 job2
redis-cli -p 6379 lrange queue 0 -1
redis-cli -p 6379 hset profile name "Ankit Negi"
redis-cli -p 6379 hgetall profile
redis-cli -p 6379 sadd skills java redis tcp
redis-cli -p 6379 smembers skills
redis-cli -p 6379 expire user:1 60
redis-cli -p 6379 ttl user:1
```

You can also run the bundled compatibility script once the server is up:

```powershell
.\scripts\redis-cli-test.ps1
```

## Supported Commands

| Category | Commands |
|----------|----------|
| Connection | `PING`, `ECHO`, `QUIT`, `SELECT`, `AUTH` |
| Strings | `SET`, `GET`, `SETEX`, `SET key value EX seconds`, `INCR`, `DECR`, `DEL`, `EXISTS` |
| Keys | `KEYS`, `TYPE`, `TTL`, `EXPIRE`, `FLUSHALL` |
| Lists | `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LLEN`, `LRANGE` |
| Hashes | `HSET`, `HGET`, `HDEL`, `HGETALL`, `HEXISTS` |
| Sets | `SADD`, `SREM`, `SMEMBERS`, `SISMEMBER` |

## How It Works

1. A client connects over TCP and sends a RESP array.
2. `VaultDBServer` reads the bytes and passes them to the RESP parser.
3. `CommandHandler` normalizes the command and dispatches it to the engine.
4. `VaultDBEngine` applies the operation under read or write locks.
5. If persistence is enabled, mutating commands are appended to the AOF file.
6. The TTL sweeper removes expired keys in the background while reads also lazily evict expired entries.

## Best Demo Path For Recruiters

1. Open the repository on GitHub.
2. Read this README to understand the project in 30 seconds.
3. Clone the repo and run `java -cp target/classes com.vaultdb.VaultDBServer --no-persistence --port 6379`.
4. Send `PING`, `SET`, and `GET` through `redis-cli` or the PowerShell snippet above.
5. Run `mvn clean test` to verify the test suite if Maven is installed.

## Tests

The repository includes 22 JUnit 5 tests covering strings, lists, hashes, sets, TTL behavior, RESP parsing, AOF replay, concurrency, and TCP integration.

## License

MIT
