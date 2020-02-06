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

package tech.pegasys.artemis.benchmarks.util.backing;

import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.PackedUnsignedLongView;

@State(Scope.Thread)
public class ListBenchmark {

  @Param({"1024"})
  int listMaxSizeM;

  private int getListMaxSize() {
    return listMaxSizeM * 1024 * 1024;
  }
  ListViewWrite<PackedUnsignedLongView> l1w;
  ListViewRead<PackedUnsignedLongView> l2r;

  public ListBenchmark() {
    ListViewType<PackedUnsignedLongView> type = new ListViewType<>(
        BasicViewTypes.PACKED_UNSIGNED_LONG_TYPE, 100_000_000);
    ListViewRead<PackedUnsignedLongView> l1 = type.createDefault();

    ListViewWrite<PackedUnsignedLongView> l2w = l1.createWritableCopy();
    for (int i = 0; i < 1000000; i++) {
      l2w.append(new PackedUnsignedLongView(UnsignedLong.valueOf(1121212)));
    }
    l2r = l2w.commitChanges();

    long s = System.nanoTime();
    l2r.hashTreeRoot();
    System.out.println("Initial hash calc: " + (System.nanoTime() - s) / 1000 + " us" );
  }

  @Setup(Level.Iteration)
  public void init() throws Exception {
    ListViewType<PackedUnsignedLongView> type = new ListViewType<>(
        BasicViewTypes.PACKED_UNSIGNED_LONG_TYPE, getListMaxSize());
    ListViewRead<PackedUnsignedLongView> l1 = type.createDefault();
    l1w = l1.createWritableCopy();
  }

  @TearDown(Level.Iteration)
  public void cleanup() throws Exception {
    ListViewRead<PackedUnsignedLongView> l1r = l1w.commitChanges();
//    System.out.println(
//        "Tree nodes count: " + TreeUtil.estimateNonDefaultNodes(l1r.getBackingNode()) + ", size: "
//            + l1r.size());
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void createDefaultUIntList(Blackhole bh) {
    ListViewType<PackedUnsignedLongView> type = new ListViewType<>(
        BasicViewTypes.PACKED_UNSIGNED_LONG_TYPE, getListMaxSize());
    ListViewRead<PackedUnsignedLongView> l1 = type.createDefault();
    bh.consume(l1);
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void append(Blackhole bh) {
    l1w.append(new PackedUnsignedLongView(UnsignedLong.valueOf(1121212)));
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void incrementalHash(Blackhole bh) {
    ListViewWrite<PackedUnsignedLongView> l2w = l2r.createWritableCopy();
    l2w.set(12345, new PackedUnsignedLongView(UnsignedLong.valueOf(77777)));
    ListViewRead<PackedUnsignedLongView> l2r_ = l2w.commitChanges();
    l2r_.hashTreeRoot();
  }
}
