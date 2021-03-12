package tech.pegasys.teku.benchmarks.util;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.openjdk.jmh.infra.Blackhole;

public class CustomRunner {

  public class RunResult {
    private final long nanos;
    private final long operationsCount;

    public RunResult(long nanos, long operationsCount) {
      this.nanos = nanos;
      this.operationsCount = operationsCount;
    }

    public double getOperationsPerSecond() {
      return operationsCount * 1_000_000_000d / nanos;
    }

    public double getNanosPerOperations() {
      return ((double) nanos) / operationsCount;
    }
  }

  private final Blackhole blackhole =
      new Blackhole(
          "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
  private final Map<String, Consumer<Blackhole>> benches = new LinkedHashMap<>();
  private Duration runDuration = Duration.ofSeconds(1);
  private int runIterations = 10;
  private int runsCount = 10;

  public CustomRunner(int runIterations, int runsCount) {
    this.runIterations = runIterations;
    this.runsCount = runsCount;
  }

  public CustomRunner withBench(String name, Consumer<Blackhole> bench) {
    benches.put(name, bench);
    return this;
  }

  public CustomRunner withBench(Consumer<Blackhole> bench) {
    benches.put("bench-" + benches.size(), bench);
    return this;
  }

  public void run() {
    benches.forEach((name, bench) -> {
      System.out.println(name);
      for (int i = 0; i < runsCount; i++) {
        RunResult result = runSingle(bench);
        System.out.printf("  %.2f ops/sec\n", result.getOperationsPerSecond());
      }
    });
  }

  private RunResult runSingle(Consumer<Blackhole> bench) {
    long start = System.nanoTime();
    long approxEnd = start + runDuration.toNanos();
    int iterations = runIterations;
    while (true) {
      bench.accept(blackhole);

      if (iterations-- == 0) {
        break;
      }
    }
    return new RunResult(System.nanoTime() - start, runIterations);
  }
}
