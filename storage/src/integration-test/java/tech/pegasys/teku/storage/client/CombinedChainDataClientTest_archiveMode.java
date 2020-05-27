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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.StateStorageMode;

public class CombinedChainDataClientTest_archiveMode extends AbstractCombinedChainDataClientTest {

  @Override
  protected StateStorageMode getStorageMode() {
    return StateStorageMode.ARCHIVE;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveHistoricalState(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState historicalBlock = chainUpdater.advanceChain();
    chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    // Sanity check
    assertThat(historicalBlock.getSlot()).isLessThan(finalizedBlock.getSlot());

    final UnsignedLong querySlot = historicalBlock.getSlot();
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(historicalBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueryBySlotParameters")
  public <T> void queryBySlot_shouldRetrieveHistoricalStateInEffectAtSkippedSlot(
      final String caseName, final QueryBySlotTestCase<T> testCase) {
    final UnsignedLong finalizedEpoch = UnsignedLong.valueOf(2);
    final UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);

    // Setup chain with finalized block
    chainUpdater.initializeGenesis();
    final SignedBlockAndState historicalBlock = chainUpdater.advanceChain();
    final UnsignedLong skippedSlot = historicalBlock.getSlot().plus(UnsignedLong.ONE);
    chainUpdater.advanceChain(skippedSlot.plus(UnsignedLong.ONE));
    chainUpdater.advanceChain(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainUpdater.finalizeEpoch(finalizedEpoch);
    chainUpdater.addNewBestBlock();

    // Sanity check
    assertThat(skippedSlot).isLessThan(finalizedBlock.getSlot());

    final UnsignedLong querySlot = skippedSlot;
    final Optional<SignedBlockAndState> effectiveBlockAtSlot = Optional.of(historicalBlock);
    final SafeFuture<Optional<T>> result = testCase.executeQueryBySlot(client, querySlot);
    final Optional<T> expected =
        testCase.mapEffectiveBlockAtSlotToExpectedResult(querySlot, effectiveBlockAtSlot);

    assertThat(result).isCompletedWithValue(expected);
  }
}
