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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.ChainBuilder.BlockOptions;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.ReorgEvent;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class ForkChoiceTest {
  private final Spec spec = SpecFactory.createMinimal();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create()
          .storageMode(StateStorageMode.PRUNE)
          .specProvider(spec)
          .numberOfValidators(16)
          .build();
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();
  private final SignedBlockAndState genesis = chainBuilder.generateGenesis();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final ForkChoice forkChoice =
      ForkChoice.create(spec, new InlineEventThread(), recentChainData);

  @BeforeEach
  public void setup() {
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);

    storageSystem
        .chainUpdater()
        .setTime(genesis.getState().getGenesis_time().plus(10L * SECONDS_PER_SLOT));
  }

  @Test
  void shouldTriggerReorgWhenEmptyHeadSlotFilled() {
    // Run fork choice with an empty slot 1
    processHead(ONE);

    // Then rerun with a filled slot 1
    final SignedBlockAndState slot1Block = storageSystem.chainUpdater().advanceChain(ONE);
    processHead(ONE);

    final List<ReorgEvent> reorgEvents = storageSystem.reorgEventChannel().getReorgEvents();
    assertThat(reorgEvents).hasSize(1);
    assertThat(reorgEvents.get(0).getBestSlot()).isEqualTo(ONE);
    assertThat(reorgEvents.get(0).getNewBestBlockRoot()).isEqualTo(slot1Block.getRoot());
  }

  @Test
  void onBlock_shouldImmediatelyMakeChildOfCurrentHeadTheNewHead() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    final SafeFuture<BlockImportResult> importResult = forkChoice.onBlock(blockAndState.getBlock());
    assertBlockImportedSuccessfully(importResult);

    assertThat(recentChainData.getHeadBlock()).contains(blockAndState.getBlock());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(blockAndState.getSlot());
  }

  @Test
  void onBlock_shouldTriggerReorgWhenSelectingChildOfChainHeadWhenForkChoiceSlotHasAdvanced() {
    // Advance the current head
    final UInt64 nodeSlot = UInt64.valueOf(5);
    processHead(nodeSlot);

    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    final SafeFuture<BlockImportResult> importResult = forkChoice.onBlock(blockAndState.getBlock());
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
    final SignedBlockAndState forkBlock1 =
        forkChain.generateBlockAtSlot(
            ONE,
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    final SignedBlockAndState betterBlock1 = chainBuilder.generateBlockAtSlot(1);

    importBlock(forkBlock1);
    // Should automatically follow the fork as its the first child block
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // Add an attestation for the fork so that it initially has higher weight
    // Otherwise ties are split based on the hash which is too hard to control in the test
    final BlockOptions forkBlockOptions = BlockOptions.create();
    forkChain
        .streamValidAttestationsWithTargetBlock(forkBlock1)
        .limit(1)
        .forEach(forkBlockOptions::addAttestation);
    final SignedBlockAndState forkBlock2 =
        forkChain.generateBlockAtSlot(forkBlock1.getSlot().plus(1), forkBlockOptions);
    importBlock(forkBlock2);

    // The fork is still the only option so gets selected
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock2.getRoot());

    // Now import what will become the canonical chain
    importBlock(betterBlock1);
    // Process head to ensure we clear any additional proposer weighting for this first block.
    // Should still pick forkBlock as it's the best option even though we have a competing chain
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock2.getRoot());

    // Import a block with two attestations which makes this chain better than the fork
    final BlockOptions options = BlockOptions.create();
    chainBuilder
        .streamValidAttestationsWithTargetBlock(betterBlock1)
        .limit(2)
        .forEach(options::addAttestation);
    final SignedBlockAndState blockWithAttestations =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(2), options);
    importBlock(blockWithAttestations);

    // Haven't run fork choice so won't have re-orged yet - fork still has more applied votes
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock2.getRoot());

    // When attestations are applied we should switch away from the fork to our better chain
    processHead(blockWithAttestations.getSlot());
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
    betterChain.forEach(this::importBlock);

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
    importBlock(blockWithAttestations);

    // Apply these votes
    processHead(blockWithAttestations.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(blockWithAttestations.getRoot());

    // Now we import the fork block
    importBlock(forkBlock);

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
    importBlock(targetBlock);

    // Attestation where the target checkpoint has a slot prior to the block it references
    final Checkpoint targetCheckpoint = new Checkpoint(ZERO, targetBlock.getRoot());
    final Attestation attestation =
        new Attestation(
            Attestation.SSZ_SCHEMA.getAggregationBitsSchema().ofBits(5),
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
                Attestation.SSZ_SCHEMA.getAggregationBitsSchema().ofBits(16),
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

  private void importBlock(final SignedBlockAndState block) {
    final SafeFuture<BlockImportResult> result = forkChoice.onBlock(block.getBlock());
    assertBlockImportedSuccessfully(result);
  }

  private void processHead(final UInt64 slot) {
    assertThat(forkChoice.processHead(slot)).isCompleted();
  }
}
