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

package tech.pegasys.teku.benchmarks.ssz;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

@Threads(1)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
public class SszAbstractContainerBenchmark<TView extends SimpleOffsetSerializable> {

  //  private final ContainerViewType<TView> containerType = getType();
  private final TView aContainer = createContainer();
  private final Bytes aContainerSsz = SimpleOffsetSerializer.serialize(aContainer);
//  private final TreeNode aContainerTree = aContainer.getBackingNode();

  // Workaround for IDEA JMH plugin: it doesn't like abstract 'State' benchmarks
  protected TView createContainer() {
    throw new UnsupportedOperationException("To override");
  }

  protected Class<TView> getContainerClass() {
    throw new UnsupportedOperationException("To override");
  }

//  protected ContainerViewType<TView> getType() {
//    throw new UnsupportedOperationException("To override");
//  }

  protected void iterateData(TView container, Blackhole bh) {
    throw new UnsupportedOperationException("To override");
  }

  @Benchmark
  public void benchCreate(Blackhole bh) {
    bh.consume(createContainer());
  }

  @Benchmark
  public void benchIterate(Blackhole bh) {
    iterateData(aContainer, bh);
  }

  @Benchmark
  public void benchCreateAndIterate(Blackhole bh) {
    TView container = createContainer();
    iterateData(container, bh);
  }

//  @Benchmark
//  public void benchCreateFromTree(Blackhole bh) {
//    bh.consume(containerType.createFromBackingNode(aContainerTree));
//  }
//
//  @Benchmark
//  public void benchCreateFromTreeAndIterate(Blackhole bh) {
//    TView container = containerType.createFromBackingNode(aContainerTree);
//    iterateData(container);
//  }

  @Benchmark
  public void benchSerialize(Blackhole bh) {
    bh.consume(SimpleOffsetSerializer.serialize(aContainer));
  }

  @Benchmark
  public void benchDeserialize(Blackhole bh) {
    bh.consume(SimpleOffsetSerializer.deserialize(aContainerSsz, getContainerClass()));
  }

  @Benchmark
  public void benchDeserializeAndIterate(Blackhole bh) {
    TView container1 = SimpleOffsetSerializer.deserialize(aContainerSsz, getContainerClass());
    iterateData(container1, bh);
  }

  public void customRun(int runs, int runLength) {
    Blackhole blackhole = new Blackhole(
        "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");

    Map<String, Consumer<Blackhole>> benches = new LinkedHashMap<>();
    benches.put("benchCreate", this::benchCreate);
    benches.put("benchIterate", this::benchIterate);
    benches.put("benchCreateAndIterate", this::benchCreateAndIterate);
//        this::benchCreateFromTree,
//        this::benchCreateFromTreeAndIterate,
    benches.put("benchSerialize", this::benchSerialize);
    benches.put("benchDeserialize", this::benchDeserialize);
    benches.put("benchDeserializeAndIterate", this::benchDeserializeAndIterate);

    for (Entry<String, Consumer<Blackhole>> entry : benches.entrySet()) {

      Consumer<Blackhole> bench = entry.getValue();
      System.out.print("Bench #" + entry.getKey() + ": ");
      for (int j = 0; j < runs; j++) {
        long s = System.currentTimeMillis();
        for (int k = 0; k < runLength; k++) {
          bench.accept(blackhole);
        }
        long t = System.currentTimeMillis() - s;
        System.out.print(t + " ");
      }
      System.out.println();
    }
  }
}
