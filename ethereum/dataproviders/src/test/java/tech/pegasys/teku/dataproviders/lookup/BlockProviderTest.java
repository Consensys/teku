/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.dataproviders.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class BlockProviderTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @Test
  void withKnownBlocks_withEmptyProvider() throws ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(2);
    final Map<Bytes32, SignedBeaconBlock> knownBlocks =
        chainBuilder
            .streamBlocksAndStates()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));

    final BlockProvider provider = BlockProvider.withKnownBlocks(BlockProvider.NOOP, knownBlocks);
    for (Bytes32 root : knownBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(knownBlocks.get(root));
    }
  }

  @Test
  void withKnownBlocks_withNonEmptyProvider() throws ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);

    final Map<Bytes32, SignedBeaconBlock> knownBlocks =
        chainBuilder
            .streamBlocksAndStates(0, 5)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> otherBlocks =
        chainBuilder
            .streamBlocksAndStates(6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));

    final BlockProvider origProvider = BlockProvider.fromMap(otherBlocks);
    final BlockProvider provider = BlockProvider.withKnownBlocks(origProvider, knownBlocks);

    // Pull known blocks
    for (Bytes32 root : knownBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(knownBlocks.get(root));
    }
    // Pull blocks from original provider
    for (Bytes32 root : otherBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(otherBlocks.get(root));
    }
  }

  @Test
  void combined() throws ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);

    final Map<Bytes32, SignedBeaconBlock> allBlocks =
        chainBuilder
            .streamBlocksAndStates()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> setA =
        chainBuilder
            .streamBlocksAndStates(0, 3)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> setB =
        chainBuilder
            .streamBlocksAndStates(4, 6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> setC =
        chainBuilder
            .streamBlocksAndStates(7, 10)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));

    final BlockProvider provider =
        BlockProvider.combined(
            BlockProvider.fromMap(setA), BlockProvider.fromMap(setB), BlockProvider.fromMap(setC));

    // Check all blocks are available
    for (Bytes32 root : allBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(allBlocks.get(root));
    }
  }

  @Test
  void fromDynamicMap() {
    chainBuilder.generateGenesis();
    SignedBlockAndState blockA = chainBuilder.generateNextBlock();
    SignedBlockAndState blockB = chainBuilder.generateNextBlock();

    final AtomicReference<Map<Bytes32, SignedBeaconBlock>> mapSupplier =
        new AtomicReference<>(Collections.emptyMap());
    final BlockProvider provider = BlockProvider.fromDynamicMap(mapSupplier::get);

    assertThat(provider.getBlock(blockA.getRoot())).isCompletedWithValue(Optional.empty());
    assertThat(provider.getBlock(blockB.getRoot())).isCompletedWithValue(Optional.empty());

    mapSupplier.set(Map.of(blockA.getRoot(), blockA.getBlock()));

    assertThat(provider.getBlock(blockA.getRoot()))
        .isCompletedWithValue(Optional.of(blockA.getBlock()));
    assertThat(provider.getBlock(blockB.getRoot())).isCompletedWithValue(Optional.empty());

    mapSupplier.set(Map.of(blockB.getRoot(), blockB.getBlock()));

    assertThat(provider.getBlock(blockA.getRoot())).isCompletedWithValue(Optional.empty());
    assertThat(provider.getBlock(blockB.getRoot()))
        .isCompletedWithValue(Optional.of(blockB.getBlock()));
  }

  @Test
  void fromMapWithLock() throws ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    final Map<Bytes32, SignedBeaconBlock> blocks =
        chainBuilder
            .streamBlocksAndStates(0)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    // 1 block is ok for this test
    assertThat(blocks.size()).isEqualTo(1);
    final SignedBeaconBlock block = blocks.values().stream().findFirst().orElseThrow();

    final ReadWriteLock lock = new ReentrantReadWriteLock();
    final Lock readLock = lock.readLock();
    final BlockProvider providerStubAsync = BlockProvider.fromMapWithLock(blocks, readLock);

    // Read block from provider
    final SafeFuture<Optional<SignedBeaconBlock>> blockFuture =
        providerStubAsync.getBlock(block.getRoot());
    assertThat(blockFuture.isCompletedNormally()).isTrue();
    assertThat(blockFuture.get()).contains(block);

    // Try the same when locked
    final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
    final BlockProvider providerAsync = BlockProvider.fromMapWithLock(blocks, readLock);
    final CountDownLatch isStarted = new CountDownLatch(1);
    final CountDownLatch isNotBlocked = new CountDownLatch(1);
    // Write lock in main thread
    lock.writeLock().lock();
    try {
      asyncRunner
          .runAsync(
              () -> {
                try {
                  // Avoids flakiness, when whole asyncRunner execution is delayed due to busy
                  // threads in parallel tests running
                  isStarted.countDown();
                  final SafeFuture<Optional<SignedBeaconBlock>> blockFutureLocked =
                      providerAsync.getBlock(block.getRoot());
                  // Attempt to read in other thread - will stuck here
                  assertThat(blockFutureLocked.get()).contains(block);
                } finally {
                  isNotBlocked.countDown();
                }
              })
          .ifExceptionGetsHereRaiseABug();
      isStarted.await();
      assertThat(isNotBlocked.await(50, TimeUnit.MILLISECONDS)).isFalse();
    } finally {
      lock.writeLock().unlock();
    }
  }
}
