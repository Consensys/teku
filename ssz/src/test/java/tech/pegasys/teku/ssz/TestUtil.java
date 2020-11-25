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

package tech.pegasys.teku.ssz;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;

public class TestUtil {

  public static class TestSubContainer extends AbstractImmutableContainer {

    public static final ContainerViewType<TestSubContainer> TYPE =
        new ContainerViewType<>(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE), TestSubContainer::new);

    private TestSubContainer(ContainerViewType<TestSubContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestSubContainer(UInt64 long1, Bytes32 bytes1) {
      super(TYPE, new UInt64View(long1), new Bytes32View(bytes1));
    }

    public UInt64 getLong1() {
      return ((UInt64View) get(0)).get();
    }

    public Bytes32 getBytes1() {
      return ((Bytes32View) get(1)).get();
    }
  }

  public static class TestContainer extends AbstractImmutableContainer {
    public static final ContainerViewType<TestContainer> TYPE =
        new ContainerViewType<>(
            List.of(TestSubContainer.TYPE, BasicViewTypes.UINT64_TYPE), TestContainer::new);

    private TestContainer(ContainerViewType<TestContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestContainer(TestSubContainer subContainer, UInt64 long1) {
      super(TYPE, subContainer, new UInt64View(long1));
    }

    public TestSubContainer getSubContainer() {
      return (TestSubContainer) get(0);
    }
    public UInt64 getLong() {
      return ((UInt64View) get(1)).get();
    }
  }

  public static <C> List<C> waitAll(List<Future<C>> futures) {
    return futures.stream()
        .map(
            f -> {
              try {
                return f.get();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  public static <C> List<Future<C>> executeParallel(Callable<C> task, int threadNum) {
    ExecutorService threadPool = Executors.newFixedThreadPool(threadNum);
    CountDownLatch latch = new CountDownLatch(threadNum);
    try {
      return IntStream.range(0, threadNum)
          .mapToObj(
              i ->
                  (Callable<C>)
                      () -> {
                        latch.countDown();
                        // start simultaneously for more aggressive concurrency
                        latch.await();
                        return task.call();
                      })
          .map(threadPool::submit)
          .collect(Collectors.toList());
    } finally {
      threadPool.shutdown();
    }
  }
}
