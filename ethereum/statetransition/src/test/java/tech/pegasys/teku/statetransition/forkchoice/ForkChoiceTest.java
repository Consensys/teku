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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.core.ForkChoiceAttestationValidator;
import tech.pegasys.teku.core.ForkChoiceBlockTasks;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.ReorgEvent;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

class ForkChoiceTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StateTransition stateTransition = new StateTransition();
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.PRUNE);
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final ForkChoice forkChoice =
      new ForkChoice(
          new ForkChoiceAttestationValidator(),
          new ForkChoiceBlockTasks(),
          new SyncForkChoiceExecutor(),
          recentChainData,
          stateTransition);

  @BeforeEach
  public void setup() {
    recentChainData.initializeFromGenesis(genesis.getState());

    storageSystem
        .chainUpdater()
        .setTime(genesis.getState().getGenesis_time().plus(10L * SECONDS_PER_SLOT));
  }

  @Test
  void shouldTriggerReorgWhenEmptyHeadSlotFilled() {
    // Run fork choice with an empty slot 1
    forkChoice.processHead(ONE);

    // Then rerun with a filled slot 1
    final SignedBlockAndState slot1Block = storageSystem.chainUpdater().advanceChain(ONE);
    forkChoice.processHead(ONE);

    final List<ReorgEvent> reorgEvents = storageSystem.reorgEventChannel().getReorgEvents();
    assertThat(reorgEvents).hasSize(1);
    assertThat(reorgEvents.get(0).getBestSlot()).isEqualTo(ONE);
    assertThat(reorgEvents.get(0).getNewBestBlockRoot()).isEqualTo(slot1Block.getRoot());
  }

  @Test
  void onBlock_shouldImmediatelyMakeChildOfCurrentHeadTheNewHead() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(
            blockAndState.getBlock(), Optional.of(processSlots(blockAndState, genesis.getState())));
    assertBlockImportedSuccessfully(importResult);

    assertThat(recentChainData.getHeadBlock()).contains(blockAndState.getBlock());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(blockAndState.getSlot());
  }

  @Test
  void onBlock_shouldTriggerReorgWhenSelectingChildOfChainHeadWhenForkChoiceSlotHasAdvanced() {
    // Advance the current head
    final UInt64 nodeSlot = UInt64.valueOf(5);
    forkChoice.processHead(nodeSlot);

    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(
            blockAndState.getBlock(), Optional.of(processSlots(blockAndState, genesis.getState())));
    assertBlockImportedSuccessfully(importResult);

    assertThat(recentChainData.getHeadBlock()).contains(blockAndState.getBlock());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(blockAndState.getSlot());
    assertThat(storageSystem.reorgEventChannel().getReorgEvents())
        .contains(
            new ReorgEvent(
                blockAndState.getRoot(),
                nodeSlot,
                blockAndState.getStateRoot(),
                genesis.getRoot(),
                genesis.getStateRoot(),
                blockAndState.getSlot().minus(1)));
  }

  @Test
  void onBlock_shouldUpdateVotesBasedOnAttestationsInBlocks() {
    final ChainBuilder forkChain = chainBuilder.fork();
    final SignedBlockAndState forkBlock =
        forkChain.generateBlockAtSlot(
            ONE,
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    final List<SignedBlockAndState> betterChain = chainBuilder.generateBlocksUpToSlot(3);

    importBlock(forkChain, forkBlock);
    // Should automatically follow the fork
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock.getRoot());

    betterChain.forEach(blockAndState -> importBlock(chainBuilder, blockAndState));
    final BlockOptions options = BlockOptions.create();
    chainBuilder
        .streamValidAttestationsForBlockAtSlot(UInt64.valueOf(4))
        .limit(3)
        .forEach(options::addAttestation);
    final SignedBlockAndState blockWithAttestations =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(4), options);
    importBlock(chainBuilder, blockWithAttestations);
    // Haven't run fork choice so won't have re-orged yet
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock.getRoot());

    // Should have processed the attestations and switched to this fork
    forkChoice.processHead(blockWithAttestations.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(blockWithAttestations.getRoot());
  }

  @Test
  void onBlock_shouldNotProcessAttestationsForBlocksThatDoNotYetExist() {
    final ChainBuilder forkChain = chainBuilder.fork();
    // Create a fork block, but don't import it.
    final SignedBlockAndState forkBlock =
        forkChain.generateBlockAtSlot(
            UInt64.valueOf(2),
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));

    // Now create the canonical chain and import.
    final List<SignedBlockAndState> betterChain = chainBuilder.generateBlocksUpToSlot(3);
    betterChain.forEach(blockAndState -> importBlock(chainBuilder, blockAndState));

    // And create a block containing an attestation for forkBlock
    final BlockOptions options = BlockOptions.create();
    final Attestation attestation =
        chainBuilder
            .streamValidAttestationsWithTargetBlock(forkBlock)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to create attestation for block "
                            + forkBlock.getRoot()
                            + " genesis root: "
                            + genesis.getRoot()
                            + " chain head: "
                            + chainBuilder.getLatestBlockAndState().getRoot()
                            + " fork block: "
                            + forkBlock.getRoot()
                            + " validators: "
                            + chainBuilder.getValidatorKeys().stream()
                                .map(BLSKeyPair::getPublicKey)
                                .map(BLSPublicKey::toString)
                                .collect(Collectors.joining(", "))));
    options.addAttestation(attestation);
    final SignedBlockAndState blockWithAttestations =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(4), options);
    importBlock(chainBuilder, blockWithAttestations);

    // Apply these votes
    forkChoice.processHead(blockWithAttestations.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(blockWithAttestations.getRoot());

    // Now we import the fork block
    importBlock(forkChain, forkBlock);

    // Then we get a later attestation from the same validator pointing to a different chain
    final UInt64 updatedAttestationSlot =
        applyAttestationFromValidator(UInt64.ZERO, blockWithAttestations);

    // And we should be able to apply the new weightings without making the fork block's weight
    // negative
    assertDoesNotThrow(() -> forkChoice.processHead(updatedAttestationSlot));
  }

  @Test
  void onAttestation_shouldBeInvalidWhenInvalidCheckpointThrown() {
    final SignedBlockAndState targetBlock = chainBuilder.generateBlockAtSlot(5);
    importBlock(chainBuilder, targetBlock);

    // Attestation where the target checkpoint has a slot prior to the block it references
    final Checkpoint targetCheckpoint = new Checkpoint(ZERO, targetBlock.getRoot());
    final Attestation attestation =
        new Attestation(
            new Bitlist(5, 5),
            new AttestationData(
                targetBlock.getSlot(),
                compute_epoch_at_slot(targetBlock.getSlot()),
                targetBlock.getRoot(),
                targetBlock.getState().getCurrent_justified_checkpoint(),
                targetCheckpoint),
            BLSSignature.empty());
    final SafeFuture<AttestationProcessingResult> result =
        forkChoice.onAttestation(ValidateableAttestation.from(attestation));
    assertThat(result)
        .isCompletedWithValue(
            AttestationProcessingResult.invalid(
                String.format(
                    "Checkpoint state (%s) must be at or prior to checkpoint slot boundary (%s)",
                    targetBlock.getSlot(), targetCheckpoint.getEpochStartSlot())));
  }

  private UInt64 applyAttestationFromValidator(
      final UInt64 validatorIndex, final SignedBlockAndState targetBlock) {
    // Note this attestation is wildly invalid but we're going to shove it straight into fork choice
    // as pre-validated.
    final UInt64 updatedAttestationSlot = UInt64.valueOf(20);
    final ValidateableAttestation updatedVote =
        ValidateableAttestation.from(
            new Attestation(
                new Bitlist(16, 16),
                new AttestationData(
                    updatedAttestationSlot,
                    UInt64.ONE,
                    targetBlock.getRoot(),
                    recentChainData.getStore().getJustifiedCheckpoint(),
                    new Checkpoint(
                        compute_epoch_at_slot(updatedAttestationSlot), targetBlock.getRoot())),
                dataStructureUtil.randomSignature()));
    updatedVote.setIndexedAttestation(
        new IndexedAttestation(
            SSZList.singleton(validatorIndex),
            updatedVote.getData(),
            updatedVote.getAttestation().getAggregate_signature()));

    forkChoice.applyIndexedAttestations(List.of(updatedVote));
    return updatedAttestationSlot;
  }

  private void assertBlockImportedSuccessfully(final SafeFuture<BlockImportResult> importResult) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = importResult.join();
    assertThat(result.isSuccessful()).describedAs(result.toString()).isTrue();
  }

  private void importBlock(final ChainBuilder chainBuilder, final SignedBlockAndState block) {
    BeaconState preState =
        processSlots(
            block,
            chainBuilder.getLatestBlockAndStateAtSlot(block.getSlot().minus(ONE)).getState());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block.getBlock(), Optional.of(preState));
    assertBlockImportedSuccessfully(result);
  }

  private BeaconState processSlots(final SignedBlockAndState block, final BeaconState preState) {
    if (preState.getSlot().isLessThan(block.getSlot())) {
      try {
        return new StateTransition().process_slots(preState, block.getSlot());
      } catch (final SlotProcessingException | EpochProcessingException e) {
        Assertions.fail("State transition failed", e);
      }
    }
    return preState;
  }
}
