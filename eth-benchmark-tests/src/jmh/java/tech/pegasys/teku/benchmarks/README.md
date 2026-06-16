# JMH Micro benchmarks

These benchmarks can be  easily run in Intellij by installing the JMH plugin.
JMH benchmarks can be run from the eth-benchmark-tests by executing JMH tests under eth-benchmark-tests

```bash
./gradlew jmh
```

JMH is a very comprehensive benchmarking library. https://github.com/openjdk/jmh

## Snappy implementation benchmark

`SnappyCompressionBenchmark` compares snappy-java (native, current gossip path) against Netty's
Snappy block codec (current RPC path) across realistic gossip payloads. It also asserts, on
startup, that the two implementations produce a mutually-decodable Snappy block wire format.

Run all payload/implementation combinations with allocation profiling:

```bash
./gradlew :eth-benchmark-tests:jmh --args="SnappyCompressionBenchmark -prof gc"
```

Run a single payload (e.g. the large data-column-sidecar case):

```bash
./gradlew :eth-benchmark-tests:jmh --args="SnappyCompressionBenchmark -prof gc -p payload=DATA_COLUMN_SIDECAR"
```

Throughput/latency comes from the standard JMH score columns; allocation per op comes from the
`gc.alloc.rate.norm` row reported by `-prof gc`.