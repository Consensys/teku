/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

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
