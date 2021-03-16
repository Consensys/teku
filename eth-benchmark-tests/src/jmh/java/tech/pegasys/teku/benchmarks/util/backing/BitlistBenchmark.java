/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.benchmarks.util.backing;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.ssz.collections.SszBitlist;
import tech.pegasys.teku.ssz.schema.collections.SszBitlistSchema;

public class BitlistBenchmark {

  static SszBitlistSchema<?> type = SszBitlistSchema.create(4096);
  static SszBitlist bitlist =
      type.ofBits(4096, IntStream.range(0, 4096).filter(i -> i % 3 == 0).toArray());

  @Benchmark
  @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void fromCachedListView(Blackhole bh) {
    bh.consume(bitlist.getAllSetBits());
  }

  @Benchmark
  @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void fromNewListView(Blackhole bh) {
    SszBitlist freshListView = type.createFromBackingNode(bitlist.getBackingNode());
    bh.consume(freshListView.getAllSetBits());
  }
}
