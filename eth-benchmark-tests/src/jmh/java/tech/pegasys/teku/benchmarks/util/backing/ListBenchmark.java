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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ListViewWrite;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;

@State(Scope.Thread)
public class ListBenchmark {

  @Param({"1024"})
  int listMaxSizeM;

  private int getListMaxSize() {
    return listMaxSizeM * 1024 * 1024;
  }

  ListViewWrite<UInt64View> l1w;
  ListViewRead<UInt64View> l2r;

  public ListBenchmark() {
    ListViewType<UInt64View> type = new ListViewType<>(BasicViewTypes.UINT64_TYPE, 100_000_000);
    ListViewRead<UInt64View> l1 = type.getDefault();

    ListViewWrite<UInt64View> l2w = l1.createWritableCopy();
    for (int i = 0; i < 1000000; i++) {
      l2w.append(new UInt64View(UInt64.valueOf(1121212)));
    }
    l2r = l2w.commitChanges();

    long s = System.nanoTime();
    l2r.hashTreeRoot();
    System.out.println("Initial hash calc: " + (System.nanoTime() - s) / 1000 + " us");
  }

  @Setup(Level.Iteration)
  public void init() throws Exception {
    ListViewType<UInt64View> type =
        new ListViewType<>(BasicViewTypes.UINT64_TYPE, getListMaxSize());
    ListViewRead<UInt64View> l1 = type.getDefault();
    l1w = l1.createWritableCopy();
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void createDefaultUIntList(Blackhole bh) {
    ListViewType<UInt64View> type =
        new ListViewType<>(BasicViewTypes.UINT64_TYPE, getListMaxSize());
    ListViewRead<UInt64View> l1 = type.getDefault();
    bh.consume(l1);
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void append(Blackhole bh) {
    l1w.append(new UInt64View(UInt64.valueOf(1121212)));
  }

  @Benchmark
  @Warmup(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  public void incrementalHash(Blackhole bh) {
    ListViewWrite<UInt64View> l2w = l2r.createWritableCopy();
    l2w.set(12345, new UInt64View(UInt64.valueOf(77777)));
    ListViewRead<UInt64View> l2r_ = l2w.commitChanges();
    l2r_.hashTreeRoot();
  }
}
