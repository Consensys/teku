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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
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
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.StubExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.ReorgEvent;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class ForkChoiceTest {

  private final Spec spec = TestSpecFactory.createMinimalMerge();
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

  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final StubExecutionEngineChannel executionEngine = new StubExecutionEngineChannel(spec);
  private ForkChoice forkChoice =
      ForkChoice.create(spec, new InlineEventThread(), recentChainData, forkChoiceNotifier, false);

  @BeforeEach
  public void setup() {
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    reset(forkChoiceNotifier); // Clear any notifications from setting genesis

    storageSystem
        .chainUpdater()
        .setTime(genesis.getState().getGenesis_time().plus(10L * spec.getSecondsPerSlot(ZERO)));
  }

  @Test
  void shouldTriggerReorgWhenEmptyHeadSlotFilled() {
    // Run fork choice with an empty slot 1
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(genesis.getRoot());

    // Then rerun with a filled slot 1
    final SignedBlockAndState slot1Block = storageSystem.chainUpdater().advanceChain(ONE);
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(slot1Block.getRoot());

    final List<ReorgEvent> reorgEvents = storageSystem.reorgEventChannel().getReorgEvents();
    assertThat(reorgEvents).hasSize(1);
    assertThat(reorgEvents.get(0).getBestSlot()).isEqualTo(ONE);
    assertThat(reorgEvents.get(0).getNewBestBlockRoot()).isEqualTo(slot1Block.getRoot());
  }

  @Test
  void onBlock_shouldImmediatelyMakeChildOfCurrentHeadTheNewHead() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(blockAndState.getBlock(), executionEngine);
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
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(blockAndState.getBlock(), executionEngine);
    assertBlockImportedSuccessfully(importResult);

    assertThat(recentChainData.getHeadBlock()).contains(blockAndState.getBlock());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(blockAndState.getSlot());
    assertThat(storageSystem.reorgEventChannel().getReorgEvents())
        .contains(
            new ReorgEvent(
                blockAndState.getRoot(),
                UInt64.valueOf(1),
                blockAndState.getStateRoot(),
                genesis.getRoot(),
                genesis.getStateRoot(),
                blockAndState.getSlot().minus(1)));
  }

  @Test
  void onBlock_shouldReorgWhenProposerWeightingMakesForkBestChain() {
    forkChoice =
        ForkChoice.create(spec, new InlineEventThread(), recentChainData, forkChoiceNotifier, true);

    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    final UInt64 lateBlockSlot = currentSlot.minus(1);
    final ChainBuilder chainB = chainBuilder.fork();
    final SignedBlockAndState chainBBlock1 =
        chainB.generateBlockAtSlot(
            lateBlockSlot,
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    final SignedBlockAndState chainABlock1 = chainBuilder.generateBlockAtSlot(lateBlockSlot);

    // All blocks received late for slot 1
    importBlock(chainABlock1);
    importBlock(chainBBlock1);

    // At this point fork choice is tied with no votes for either chain
    // The winner is the block with the greatest hash which is hard to control.
    // So just find which block won and check that we can switch forks based on proposer reward
    final SignedBlockAndState expectedChainHead;
    if (recentChainData.getChainHead().orElseThrow().getRoot().equals(chainABlock1.getRoot())) {
      // ChainA won, so try to switch to chain B
      expectedChainHead = chainB.generateBlockAtSlot(currentSlot);
    } else {
      // ChainB won so try to switch to chain A
      expectedChainHead = chainBuilder.generateBlockAtSlot(currentSlot);
    }

    importBlock(expectedChainHead);
    assertThat(recentChainData.getStore().getProposerBoostRoot())
        .contains(expectedChainHead.getRoot());

    assertThat(forkChoice.processHead()).isCompleted();

    // Check we switched chains, if proposer reward wasn't considered we'd stay on the other fork
    assertThat(recentChainData.getBestBlockRoot()).contains(expectedChainHead.getRoot());
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
  void onBlock_shouldHandleNonCanonicalBlockThatUpdatesJustifiedCheckpoint() {
    // If the new block is not the child of the current head block we use `ProtoArray.findHead`
    // to check if it should become the new head.  If importing that block caused the justified
    // checkpoint to be updated, then the justified epoch in ProtoArray won't match the justified
    // epoch of the new head so it considers the head invalid.  Normally that update is done when
    // applying pending votes.

    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    final UInt64 epoch4StartSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(4));

    // Set the time to be the start of epoch 3 so all the blocks we need are valid
    chainUpdater.setTime(
        spec.getSlotStartTime(epoch4StartSlot, genesis.getState().getGenesis_time()));

    justifyEpoch(chainUpdater, 2);

    // Update ProtoArray to avoid the special case of considering the anchor
    // epoch as allowing all nodes to be a valid head.
    processHead(epoch4StartSlot);

    prepEpochForJustification(chainUpdater, epoch4StartSlot);

    // Switch head to a different fork so the next block has to use findHead
    chainUpdater.updateBestBlock(chainBuilder.fork().generateNextBlock());

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(epoch4StartSlot);
    importBlock(epoch4Block);

    // Should now have justified epoch 3
    assertThat(recentChainData.getJustifiedCheckpoint())
        .map(Checkpoint::getEpoch)
        .contains(UInt64.valueOf(3));

    // The only block with the newly justified checkpoint is epoch4Block so it should become head
    assertThat(recentChainData.getBestBlockRoot()).contains(epoch4Block.getRoot());
  }

  @Test
  void onBlock_shouldSendForkChoiceUpdatedNotification() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(blockAndState.getBlock(), executionEngine);
    assertBlockImportedSuccessfully(importResult);

    assertForkChoiceUpdateNotification(blockAndState);
  }

  @Test
  void onBlock_shouldUpdateLatestValidFinalizedSlotPreMergeBlock() {
    // make EL returning INVALID, but will never be called
    executionEngine.setExecutePayloadResult(
        ExecutePayloadResult.invalid(Optional.empty(), Optional.empty()));

    UInt64 slotToImport = prepFinalizeEpoch(2);

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    // Should now have finalized epoch 2
    assertThat(recentChainData.getFinalizedEpoch()).isEqualTo(UInt64.valueOf(2));
    assertThat(recentChainData.getLatestValidFinalizedSlot()).isEqualTo(UInt64.valueOf(16));
  }

  @Test
  void onBlock_shouldUpdateLatestValidFinalizedSlotPostMergeBlock() {
    doMerge();
    UInt64 slotToImport = prepFinalizeEpoch(2);

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    // Should now have finalized epoch 2
    assertThat(recentChainData.getFinalizedEpoch()).isEqualTo(UInt64.valueOf(2));

    // latest valid finalized should have advanced to 16
    assertThat(recentChainData.getLatestValidFinalizedSlot()).isEqualTo(UInt64.valueOf(16));
  }

  @Test
  void onBlock_shouldNotOptimisticallyImportBeforeFinalizedExecutionPayloadELSyncing() {
    doMerge();
    UInt64 slotToImport = prepFinalizeEpoch(2);

    // make EL returning SYNCING
    executionEngine.setExecutePayloadResult(ExecutePayloadResult.SYNCING);

    // generate block which finalize epoch 2
    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlockWithError(epoch4Block, FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);
  }

  @Test
  void onBlock_shouldNotOptimisticallyImportBeforeFinalizedExecutionPayloadELFailure() {
    doMerge();
    UInt64 slotToImport = prepFinalizeEpoch(2);

    // make EL returning low level error
    executionEngine.setExecutePayloadResult(
        ExecutePayloadResult.failedExecution(new RuntimeException("net error")));

    // generate block which finalize epoch 2
    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlockWithError(epoch4Block, FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION);
  }

  @Test
  void onBlock_shouldNotUpdateLatestValidFinalizedSlotWhenOptimisticallyImported() {
    doMerge();
    UInt64 slotToImport = prepFinalizeEpoch(2);

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    slotToImport = prepFinalizeEpoch(4);

    // make EL returning SYNCING
    executionEngine.setExecutePayloadResult(ExecutePayloadResult.SYNCING);

    // generate block which finalize epoch 4
    final SignedBlockAndState epoch6Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch6Block);

    // Should now have finalized epoch 3
    assertThat(recentChainData.getFinalizedEpoch()).isEqualTo(UInt64.valueOf(4));

    // latest valid finalized slot should remain 16
    assertThat(recentChainData.getLatestValidFinalizedSlot()).isEqualTo(UInt64.valueOf(24));

    // we now simulate an update from OptimisticHeadValidator
    forkChoice.onExecutionPayloadResult(
        epoch6Block.getRoot(),
        ExecutePayloadResult.VALID,
        recentChainData.getStore().getLatestFinalizedBlockSlot());

    // latest valid finalized should have advanced to 32
    assertThat(recentChainData.getLatestValidFinalizedSlot()).isEqualTo(UInt64.valueOf(32));
  }

  @Test
  void applyHead_shouldSendForkChoiceUpdatedNotification() {
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().advanceChainUntil(1);

    processHead(ONE);

    assertForkChoiceUpdateNotification(blockAndState);
  }

  private void assertForkChoiceUpdateNotification(final SignedBlockAndState blockAndState) {
    final ForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    final Bytes32 headExecutionHash =
        forkChoiceStrategy.executionBlockHash(blockAndState.getRoot()).orElseThrow();
    final Bytes32 finalizedExecutionHash =
        forkChoiceStrategy
            .executionBlockHash(recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot())
            .orElseThrow();
    verify(forkChoiceNotifier)
        .onForkChoiceUpdated(
            new ForkChoiceState(headExecutionHash, headExecutionHash, finalizedExecutionHash));
  }

  private void justifyEpoch(final ChainUpdater chainUpdater, final long epoch) {
    final UInt64 nextEpochStartSlot =
        spec.computeStartSlotAtEpoch(UInt64.valueOf(epoch + 1))
            .max(chainUpdater.getHeadSlot().plus(1));
    // Advance chain to an epoch we can actually justify
    prepEpochForJustification(chainUpdater, nextEpochStartSlot);

    // Trigger epoch transition into next epoch.
    importBlock(chainBuilder.generateBlockAtSlot(nextEpochStartSlot));

    // Should now have justified epoch
    assertThat(recentChainData.getJustifiedCheckpoint())
        .map(Checkpoint::getEpoch)
        .contains(UInt64.valueOf(epoch));
  }

  private void prepEpochForJustification(
      final ChainUpdater chainUpdater, final UInt64 nextEpochStartSlot) {
    UInt64 headSlot = chainUpdater.getHeadSlot();
    final UInt64 targetHeadSlot = nextEpochStartSlot.minus(2);
    while (headSlot.isLessThan(targetHeadSlot)) {
      SignedBlockAndState headBlock = chainBuilder.generateBlockAtSlot(headSlot.plus(1));
      importBlock(headBlock);
      assertThat(recentChainData.getBestBlockRoot()).contains(headBlock.getRoot());
      headSlot = headBlock.getSlot();
    }

    // Add a block with enough attestations to justify the epoch.
    final BlockOptions epoch2BlockOptions = BlockOptions.create();
    final UInt64 newBlockSlot = headSlot.increment();
    chainBuilder
        .streamValidAttestationsForBlockAtSlot(newBlockSlot)
        .forEach(epoch2BlockOptions::addAttestation);
    final SignedBlockAndState epoch2Block =
        chainBuilder.generateBlockAtSlot(newBlockSlot, epoch2BlockOptions);

    importBlock(epoch2Block);
  }

  private UInt64 prepFinalizeEpoch(long epoch) {
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    final UInt64 epochPlus2StartSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(epoch).plus(2));

    chainUpdater.setTime(
        spec.getSlotStartTime(epochPlus2StartSlot, genesis.getState().getGenesis_time()));

    justifyEpoch(chainUpdater, epoch);

    prepEpochForJustification(chainUpdater, epochPlus2StartSlot);

    return epochPlus2StartSlot;
  }

  private void doMerge() {
    final SignedBlockAndState epoch4Block =
        chainBuilder.generateBlockAtSlot(
            storageSystem.chainUpdater().getHeadSlot().plus(1),
            ChainBuilder.BlockOptions.create()
                .setTerminalBlockHash(dataStructureUtil.randomBytes32()));

    storageSystem.chainUpdater().updateBestBlock(epoch4Block);
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
                spec.computeEpochAtSlot(targetBlock.getSlot()),
                targetBlock.getRoot(),
                targetBlock.getState().getCurrent_justified_checkpoint(),
                targetCheckpoint),
            BLSSignature.empty());
    final SafeFuture<AttestationProcessingResult> result =
        forkChoice.onAttestation(ValidateableAttestation.from(spec, attestation));
    assertThat(result)
        .isCompletedWithValue(
            AttestationProcessingResult.invalid(
                String.format(
                    "Checkpoint state (%s) must be at or prior to checkpoint slot boundary (%s)",
                    targetBlock.getSlot(), targetCheckpoint.getEpochStartSlot(spec))));
  }

  private UInt64 applyAttestationFromValidator(
      final UInt64 validatorIndex, final SignedBlockAndState targetBlock) {
    // Note this attestation is wildly invalid but we're going to shove it straight into fork choice
    // as pre-validated.
    final UInt64 updatedAttestationSlot = UInt64.valueOf(20);
    final ValidateableAttestation updatedVote =
        ValidateableAttestation.from(
            spec,
            new Attestation(
                Attestation.SSZ_SCHEMA.getAggregationBitsSchema().ofBits(16),
                new AttestationData(
                    updatedAttestationSlot,
                    UInt64.ONE,
                    targetBlock.getRoot(),
                    recentChainData.getStore().getJustifiedCheckpoint(),
                    new Checkpoint(
                        spec.computeEpochAtSlot(updatedAttestationSlot), targetBlock.getRoot())),
                dataStructureUtil.randomSignature()));
    updatedVote.setIndexedAttestation(
        new IndexedAttestation(
            IndexedAttestation.SSZ_SCHEMA.getAttestingIndicesSchema().of(validatorIndex),
            updatedVote.getData(),
            updatedVote.getAttestation().getAggregateSignature()));

    forkChoice.applyIndexedAttestations(List.of(updatedVote));
    return updatedAttestationSlot;
  }

  private void assertBlockImportedSuccessfully(final SafeFuture<BlockImportResult> importResult) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = importResult.join();
    assertThat(result.isSuccessful()).describedAs(result.toString()).isTrue();
  }

  private void importBlock(final SignedBlockAndState block) {
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block.getBlock(), executionEngine);
    assertBlockImportedSuccessfully(result);
  }

  private void assertBlockImportFailure(
      final SafeFuture<BlockImportResult> importResult, FailureReason failureReason) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = importResult.join();
    assertThat(result.getFailureReason()).isEqualTo(failureReason);
  }

  private void importBlockWithError(final SignedBlockAndState block, FailureReason failureReason) {
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block.getBlock(), executionEngine);
    assertBlockImportFailure(result, failureReason);
  }

  private void processHead(final UInt64 slot) {
    assertThat(forkChoice.processHead(slot)).isCompleted();
  }
}
