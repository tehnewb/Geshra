# Performance and memory usage

The library reduces per-update allocation and repeated work in DOM serialization, component lookup, keyboard dispatch, and static serving. Optimizations preserve the browser protocol, typed event routing, structural update ordering, and synchronous application callbacks.

## Measured hot paths

The following JMH measurements use JDK 21 on the development Windows machine. Each run used one fork, two one-second warmups, three one-second measurements, and the GC profiler. These short microbenchmarks isolate operations; they do not measure complete application latency or CPU instruction counts. Timings depend on workload and hardware, and the raw results include uncertainty estimates.

| Workload | Before ns per operation | After ns per operation | Before allocated bytes | After allocated bytes |
| --- | ---: | ---: | ---: | ---: |
| Encode and send 64 text mutations | 17507 | 4168 | 54916 | 4991 |
| Register and look up 64 component IDs | 577 | 363 | 3432 | 2296 |
| Construct a standalone style with two properties | 294 | 87 | 1296 | 832 |
| Look up the common key ArrowLeft | 71.6 | 1.24 | 608 | Approximately 0 |

The DOM workload is about 4.2 times faster and allocates about 91 percent less heap memory. Component-registry allocation falls by about 33 percent. The keyboard workload reuses its key string, so real network input also includes string decoding and hashing costs.

The [benchmark source](src/jmh/java/geshra/benchmark/GeshraBenchmark.java), [baseline results](benchmarks/results/baseline.json), and [optimized results](benchmarks/results/optimized.json) define and record these workloads. Allocation counts cover Java heap allocation; pooled direct memory and retained caches require separate accounting.

## Changes to serialization and UI updates

DOM mutations store their common one or two parameters inline. Additional keys use compact arrays, replacing linked maps and entry objects. Internal component setters avoid temporary parameter maps. UTF-8 values are written directly into an outgoing buffer without intermediate byte arrays or one direct buffer per mutation.

Dispatchers allocate queue storage only when used. Monotonic priorities need no sorting; mixed priorities use stable ordering. Queue insertion preserves explicit priorities, fixing integer overflow in maximum-priority property updates. Ordinary packets target 64 KiB and obey the unsigned 16-bit mutation count. A single mutation is indivisible and can exceed the target size if its individual parameters fit the protocol limits.

UI component lookup uses an existing Netty primitive-key map, and released IDs use a primitive stack. Dirty components link directly into their owning UI's pending list, so sending changes visits affected components instead of repeatedly traversing the entire tree. WebSocket actions group mutations automatically. Explicit `beginUpdate` and `endUpdate` support nested bulk changes outside that path.

Slow connections pause inbound reads and retain unsent mutations until output becomes writable. Script execution and navigation establish a DOM ordering barrier. Unicode title and parameter lengths count encoded bytes; oversized values fail explicitly and partially encoded buffers are released.

The Valthorne event publisher already dispatches through immutable arrays without allocation, reflection, or locks on the publication path. Styles have only one constructor-supplied callback, so they invoke its `EventHandler` directly instead of allocating a routing table. Style lookup and inline output avoid copied declaration lists. Packet opcodes use an immutable array index, and protocol enum lookups use precomputed explicit-code tables. Keyboard names use a single initialized index that preserves the original handling of duplicate browser key names.

## Static assets and lifecycle

One shared static handler serves all connections of a server. Immutable packaged resources reuse retained payloads and gzip representations; filesystem resources stay uncached so development edits remain visible. Both representations count toward `geshra.web.static-cache-bytes`, which defaults to 8 MiB and is a limit rather than an upfront allocation. Entry count is capped at 256 to bound metadata, including zero-byte files.

Set `geshra.web.static-cache-bytes=0` to trade repeated reads and compression for less retained payload memory. Assets above one MiB stream in 8 KiB chunks through Netty instead of allocating a complete file-sized response array. HEAD responses use available metadata without reading the body. The cache is discarded at server shutdown, and sessions belonging to that listener are invalidated immediately rather than retaining UI trees until the inactivity timeout.

The runtime uses the Netty HTTP module and its required foundations rather than the complete Netty aggregation. JMH is isolated to its own development source set and is not a consumer dependency. The library does not change the host application's JVM heap, garbage collector, worker workload, or authentication policy.

## Run the benchmarks

Use JDK 21 and the repository wrapper. On Windows, replace `./gradlew` with `.\gradlew.bat`.

```sh
./gradlew jmh "-PjmhArgs=GeshraBenchmark -wi 2 -i 3 -w 1s -r 1s -f 1 -prof gc -rf json -rff build/jmh-results.json"
```

The default `jmh` task uses longer runs with two forks. For release decisions, repeat with production-sized renders, realistic dynamic input, representative concurrency, and CPU and retained-memory profiling. The included tests cover batching, ordering, Unicode limits, buffer ownership, packet splitting, backpressure, gzip negotiation, cache bounds, live files, and streaming through a real HTTP connection.
