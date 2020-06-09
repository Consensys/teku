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

package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockValidatorTest {
  private final EventBus eventBus = new EventBus();

  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(eventBus);
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(10, recentChainData);

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

    InternalValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(InternalValidationResult.ACCEPT);
  }

  @Test
  void shouldReturnInvalidForSecondValidBlockForSlotAndProposer() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    beaconChainUtil.setSlot(nextSlot);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);

    InternalValidationResult result1 = blockValidator.validate(block);
    assertThat(result1).isEqualTo(InternalValidationResult.ACCEPT);

    InternalValidationResult result2 = blockValidator.validate(block);
    assertThat(result2).isEqualTo(InternalValidationResult.IGNORE);
  }

  @Test
  void shouldReturnSavedForFutureForBlockFromFuture() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(nextSlot);

    InternalValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(InternalValidationResult.SAVE_FOR_FUTURE);
  }

  @Test
  void shouldReturnSavedForFutureForBlockWithParentUnavailable() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    beaconChainUtil.setSlot(nextSlot);

    final SignedBeaconBlock signedBlock = beaconChainUtil.createBlockAtSlot(nextSlot);
    final UnsignedLong proposerIndex = signedBlock.getMessage().getProposer_index();
    final BeaconBlock block =
        new BeaconBlock(
            signedBlock.getSlot(),
            proposerIndex,
            Bytes32.ZERO,
            signedBlock.getMessage().getState_root(),
            signedBlock.getMessage().getBody());

    BLSSignature blockSignature =
        new Signer(beaconChainUtil.getSigner(proposerIndex.intValue()))
            .signBlock(block, recentChainData.getBestState().get().getForkInfo())
            .join();
    final SignedBeaconBlock blockWithNoParent = new SignedBeaconBlock(block, blockSignature);

    InternalValidationResult result = blockValidator.validate(blockWithNoParent);
    assertThat(result).isEqualTo(InternalValidationResult.SAVE_FOR_FUTURE);
  }

  @Test
  void shouldReturnInvalidForBlockOlderThanFinalizedSlot() throws Exception {
    UnsignedLong finalizedEpoch = UnsignedLong.valueOf(10);
    UnsignedLong finalizedSlot = compute_start_slot_at_epoch(finalizedEpoch);
    final SignedBeaconBlock block = beaconChainUtil.createBlockAtSlot(finalizedSlot.minus(ONE));
    beaconChainUtil.finalizeChainAtEpoch(finalizedEpoch);
    beaconChainUtil.setSlot(recentChainData.getBestSlot());

    InternalValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(InternalValidationResult.IGNORE);
  }

  @Test
  void shouldReturnInvalidForBlockWithWrongProposerIndex() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    beaconChainUtil.setSlot(nextSlot);

    final SignedBeaconBlock signedBlock = beaconChainUtil.createBlockAtSlot(nextSlot);

    UnsignedLong invalidProposerIndex = signedBlock.getMessage().getProposer_index().minus(ONE);

    final BeaconBlock block =
        new BeaconBlock(
            signedBlock.getSlot(),
            invalidProposerIndex,
            signedBlock.getParent_root(),
            signedBlock.getMessage().getState_root(),
            signedBlock.getMessage().getBody());

    BLSSignature blockSignature =
        new Signer(beaconChainUtil.getSigner(invalidProposerIndex.intValue()))
            .signBlock(block, recentChainData.getBestState().get().getForkInfo())
            .join();
    final SignedBeaconBlock invalidProposerSignedBlock =
        new SignedBeaconBlock(block, blockSignature);

    InternalValidationResult result = blockValidator.validate(invalidProposerSignedBlock);
    assertThat(result).isEqualTo(InternalValidationResult.REJECT);
  }

  @Test
  void shouldReturnInvalidForBlockWithWrongSignature() throws Exception {
    final UnsignedLong nextSlot = recentChainData.getBestSlot().plus(ONE);
    beaconChainUtil.setSlot(nextSlot);

    final SignedBeaconBlock block =
        new SignedBeaconBlock(
            beaconChainUtil.createBlockAtSlot(nextSlot).getMessage(), BLSSignature.random(0));

    InternalValidationResult result = blockValidator.validate(block);
    assertThat(result).isEqualTo(InternalValidationResult.REJECT);
  }
}
