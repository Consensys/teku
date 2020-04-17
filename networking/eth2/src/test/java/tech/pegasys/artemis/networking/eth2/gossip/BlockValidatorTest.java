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

package tech.pegasys.artemis.networking.eth2.gossip;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.BlockValidationResult;
import tech.pegasys.artemis.networking.eth2.gossip.topics.validation.BlockValidator;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.artemis.storage.client.RecentChainData;

public class BlockValidatorTest {
  private final EventBus eventBus = new EventBus();

  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(2, recentChainData);

  private BlockValidator blockValidator;

  @BeforeEach
  void setUp() {
    beaconChainUtil.initializeStorage();
    blockValidator = new BlockValidator(recentChainData, new StateTransition());
  }

  @Test
  void shouldReturnValidForValidBlock() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    beaconChainUtil.setSlot(nextSlot);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);

    BlockValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(BlockValidationResult.VALID);
  }

  @Test
  void shouldReturnInvalidForSecondValidBlockForSlotAndProposer() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    beaconChainUtil.setSlot(nextSlot);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);

    BlockValidationResult result1 = blockValidator.validate(block);
    assertThat(result1).isEqualTo(BlockValidationResult.VALID);

    BlockValidationResult result2 = blockValidator.validate(block);
    assertThat(result2).isEqualTo(BlockValidationResult.INVALID);
  }

  @Test
  void shouldReturnSavedForFutureForBlockFromFuture() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);

    BlockValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(BlockValidationResult.SAVED_FOR_FUTURE);
  }

  @Test
  void shouldReturnInvalidForBlockOlderThanFinalizedSlot() throws Exception {
    UnsignedLong finalizedEpoch = UnsignedLong.valueOf(10);
    UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(finalizedSlot.minus(ONE));
    beaconChainUtil.finalizeChainAtEpoch(finalizedEpoch);
    beaconChainUtil.setSlot(recentChainData.getBestSlot());

    BlockValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(BlockValidationResult.INVALID);
  }

  @Test
  void shouldReturnInvalidForBlockProposedByUnexpectedProposer() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    beaconChainUtil.setSlot(nextSlot);
    final SignedBeaconBlock block =
        beaconChainUtil.createBlockAtSlotFromInvalidProposerIndexWithValidSignature(nextSlot);

    BlockValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(BlockValidationResult.INVALID);
  }

  @Test
  void shouldReturnInvalidForBlockProposedByExpectedProposerButWithWrongSignature()
      throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    beaconChainUtil.setSlot(nextSlot);
    final SignedBeaconBlock block =
        beaconChainUtil.createBlockAtSlotFromValidProposerIndexWithInvalidSignature(nextSlot);

    BlockValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(BlockValidationResult.INVALID);
  }
}
