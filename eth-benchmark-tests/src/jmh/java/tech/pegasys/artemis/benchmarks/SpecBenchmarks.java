package tech.pegasys.artemis.benchmarks;

import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;

@State(Scope.Thread)
public class SpecBenchmarks {

  UnsignedLong n = UnsignedLong.valueOf(32L * (32 * 1024) * 1_000_000_000);

  @Benchmark
  @Warmup(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void integerSquareRoot(Blackhole bh) {
    n = n.plus(UnsignedLong.ONE);
    bh.consume(BeaconStateUtil.integer_squareroot(n));
  }
}
