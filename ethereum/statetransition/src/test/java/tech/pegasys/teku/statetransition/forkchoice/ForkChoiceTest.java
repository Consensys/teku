/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_UPDATE_HEAD_ON_BLOCK_IMPORT_ENABLED;
import static tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarAndValidationResult.validResult;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoice.BLOCK_CREATION_TOLERANCE_MS;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.mockito.verification.VerificationMode;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarAndValidationResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobsSidecarManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.ReorgEvent;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class ForkChoiceTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BlobsSidecarManager blobsSidecarManager = mock(BlobsSidecarManager.class);
  private final BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker =
      mock(BlobsSidecarAvailabilityChecker.class);
  private final AttestationSchema attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
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
  private final OptimisticHeadSubscriber optimisticSyncStateTracker =
      mock(OptimisticHeadSubscriber.class);
  private final ExecutionLayerChannelStub executionLayer =
      new ExecutionLayerChannelStub(spec, false, Optional.empty());
  private final MergeTransitionBlockValidator transitionBlockValidator =
      mock(MergeTransitionBlockValidator.class);

  private final InlineEventThread eventThread = new InlineEventThread();

  private ForkChoice forkChoice =
      new ForkChoice(
          spec,
          eventThread,
          recentChainData,
          blobsSidecarManager,
          forkChoiceNotifier,
          new ForkChoiceStateProvider(eventThread, recentChainData),
          new TickProcessor(spec, recentChainData),
          transitionBlockValidator,
          DEFAULT_FORK_CHOICE_UPDATE_HEAD_ON_BLOCK_IMPORT_ENABLED);

  @BeforeEach
  public void setup() {
    when(transitionBlockValidator.verifyAncestorTransitionBlock(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.VALID);
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    reset(
        forkChoiceNotifier,
        transitionBlockValidator); // Clear any notifications from setting genesis

    // by default everything is valid
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.VALID);
    when(transitionBlockValidator.verifyAncestorTransitionBlock(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));

    forkChoice.subscribeToOptimisticHeadChangesAndUpdate(optimisticSyncStateTracker);

    // blobs always available
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      final BlobsSidecar blobsSidecar = dataStructureUtil.randomBlobsSidecar();
      when(blobsSidecarAvailabilityChecker.initiateDataAvailabilityCheck()).thenReturn(true);
      when(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
          .thenReturn(SafeFuture.completedFuture(validResult(blobsSidecar)));
      when(blobsSidecarManager.createAvailabilityChecker(any()))
          .thenReturn(blobsSidecarAvailabilityChecker);
    }
  }

  @Test
  void shouldNotTriggerReorgWhenEmptyHeadSlotFilled() {
    // Run fork choice with an empty slot 1
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(genesis.getRoot());

    // Then rerun with a filled slot 1
    final SignedBlockAndState slot1Block = storageSystem.chainUpdater().advanceChain(ONE);
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(slot1Block.getRoot());

    final List<ReorgEvent> reorgEvents = storageSystem.chainHeadChannel().getReorgEvents();
    assertThat(reorgEvents).isEmpty();
  }

  @Test
  void onBlock_shouldCheckBlobsAvailability() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    importBlock(blockAndState);

    verify(blobsSidecarManager).createAvailabilityChecker(blockAndState.getBlock());
    verify(blobsSidecarAvailabilityChecker).initiateDataAvailabilityCheck();
    verify(blobsSidecarAvailabilityChecker).getAvailabilityCheckResult();
  }

  @Test
  void onBlock_shouldFailIfBlobsAreNotAvailable() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    when(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(SafeFuture.completedFuture(BlobsSidecarAndValidationResult.NOT_AVAILABLE));

    importBlockWithError(blockAndState, FailureReason.FAILED_BLOBS_AVAILABILITY_CHECK);

    verify(blobsSidecarManager).createAvailabilityChecker(blockAndState.getBlock());
    verify(blobsSidecarAvailabilityChecker).initiateDataAvailabilityCheck();
    verify(blobsSidecarAvailabilityChecker).getAvailabilityCheckResult();
  }

  @Test
  void onBlock_shouldImportIfBlobsAreNotRequired() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    when(blobsSidecarAvailabilityChecker.getAvailabilityCheckResult())
        .thenReturn(SafeFuture.completedFuture(BlobsSidecarAndValidationResult.NOT_REQUIRED));

    importBlock(blockAndState);

    verify(blobsSidecarManager).createAvailabilityChecker(blockAndState.getBlock());
    verify(blobsSidecarAvailabilityChecker).initiateDataAvailabilityCheck();
    verify(blobsSidecarAvailabilityChecker).getAvailabilityCheckResult();
  }

  @Test
  void onBlock_shouldImmediatelyMakeChildOfCurrentHeadTheNewHead() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(blockAndState.getBlock(), Optional.empty(), executionLayer);
    assertBlockImportedSuccessfully(importResult, false);

    assertThat(recentChainData.getHeadBlock().map(MinimalBeaconBlockSummary::getRoot))
        .contains(blockAndState.getRoot());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(blockAndState.getSlot());
  }

  @Test
  void onBlock_shouldNotTriggerReorgWhenSelectingChildOfChainHeadWhenForkChoiceSlotHasAdvanced() {
    // Advance the current head
    final UInt64 nodeSlot = UInt64.valueOf(5);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(UInt64.valueOf(5));
    processHead(nodeSlot);

    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(blockAndState.getBlock(), Optional.empty(), executionLayer);
    assertBlockImportedSuccessfully(importResult, false);

    assertThat(recentChainData.getHeadBlock().map(MinimalBeaconBlockSummary::getRoot))
        .contains(blockAndState.getRoot());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(blockAndState.getSlot());
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();
  }

  private static Stream<Arguments> provideArgumentsForShouldReorg() {
    return Stream.of(
        Arguments.of(0, true),
        Arguments.of(500, true),
        Arguments.of(1700, true),
        Arguments.of(2300, false));
  }

  @ParameterizedTest
  @MethodSource("provideArgumentsForShouldReorg")
  void onBlock_shouldReorgWhenProposerWeightingMakesForkBestChain(
      long advanceTimeSlotMillis, boolean shouldReorg) {
    storageSystem.chainUpdater().setCurrentSlot(UInt64.valueOf(2));
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            BlobsSidecarManager.NOOP,
            forkChoiceNotifier,
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            DEFAULT_FORK_CHOICE_UPDATE_HEAD_ON_BLOCK_IMPORT_ENABLED);

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

    // advance time into slot
    if (advanceTimeSlotMillis > 0) {
      StoreTransaction transaction = recentChainData.startStoreTransaction();
      UInt64 timeIntoSlotMillis =
          recentChainData.getStore().getTimeMillis().plus(advanceTimeSlotMillis);
      transaction.setTimeMillis(timeIntoSlotMillis);
      SafeFutureAssert.safeJoin(transaction.commit());
    }

    importBlock(expectedChainHead);

    if (shouldReorg) {
      assertThat(recentChainData.getStore().getProposerBoostRoot())
          .contains(expectedChainHead.getRoot());
      assertThat(forkChoice.processHead()).isCompleted();
      // Check we switched chains, if proposer reward wasn't considered we'd stay on the other fork
      assertThat(recentChainData.getBestBlockRoot()).hasValue(expectedChainHead.getRoot());
    } else {
      assertThat(recentChainData.getStore().getProposerBoostRoot()).isEmpty();
      assertThat(forkChoice.processHead()).isCompleted();
      // no reorg
      assertThat(recentChainData.getBestBlockRoot())
          .hasValueSatisfying(
              bestBlockRoot -> assertThat(bestBlockRoot).isNotEqualTo(expectedChainHead.getRoot()));
    }
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
  void onBlock_shouldUpdateVotesBasedOnAttesterSlashingEquivocationsInBlocks() {
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
    List<Attestation> forkAttestations =
        forkChain
            .streamValidAttestationsWithTargetBlock(forkBlock1)
            .limit(2)
            .collect(Collectors.toList());
    forkAttestations.forEach(forkBlockOptions::addAttestation);
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

    // Import a block with one attestation on what will be better chain
    final BlockOptions options = BlockOptions.create();
    chainBuilder
        .streamValidAttestationsWithTargetBlock(betterBlock1)
        .limit(1)
        .forEach(options::addAttestation);
    final SignedBlockAndState blockWithAttestations =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(2), options);
    importBlock(blockWithAttestations);

    // Haven't run fork choice so won't have re-orged yet - fork still has more applied votes
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock2.getRoot());

    // Verify that fork is still better
    processHead(blockWithAttestations.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock2.getRoot());

    // Add 2 AttesterSlashing on betterBlock chain, so it will become finally better
    final BlockOptions options2 = BlockOptions.create();
    forkAttestations.forEach(
        attestation ->
            options2.addAttesterSlashing(
                chainBuilder.createAttesterSlashingForAttestation(attestation, forkBlock1)));
    final SignedBlockAndState blockWithAttesterSlashings =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(3), options2);
    importBlock(blockWithAttesterSlashings);

    // Haven't run fork choice so won't have re-orged yet - fork still has more applied votes
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock2.getRoot());

    // When attester slashings are applied we should switch away from the fork to our better chain
    processHead(blockWithAttesterSlashings.getSlot());

    assertThat(recentChainData.getBestBlockRoot()).contains(blockWithAttesterSlashings.getRoot());
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
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(blockAndState.getBlock(), Optional.empty(), executionLayer);
    assertBlockImportedSuccessfully(importResult, false);

    assertForkChoiceUpdateNotification(blockAndState, false);
  }

  @Test
  void onBlock_shouldNotOptimisticallyImportRecentMergeBlock() {
    final SignedBlockAndState epoch4Block = generateMergeBlock();
    // make EL returning SYNCING
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);

    importBlockWithError(epoch4Block, FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);
  }

  @Test
  void onBlock_shouldNotOptimisticallyImportOnELFailure() {
    doMerge();
    UInt64 slotToImport = recentChainData.getHeadSlot().plus(1);

    // make EL returning low level error
    executionLayer.setPayloadStatus(
        PayloadStatus.failedExecution(new RuntimeException("net error")));

    // generate block which finalize epoch 2
    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlockWithError(epoch4Block, FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION);
  }

  @Test
  void onBlock_shouldNotOptimisticallyImportInvalidExecutionPayload() {
    doMerge();
    UInt64 slotToImport = prepFinalizeEpoch(2);

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    // make EL returning INVALID
    executionLayer.setPayloadStatus(PayloadStatus.invalid(Optional.empty(), Optional.empty()));

    storageSystem.chainUpdater().setCurrentSlot(slotToImport.increment());
    importBlockWithError(chainBuilder.generateNextBlock(), FailureReason.FAILED_STATE_TRANSITION);
  }

  @Test
  void onBlock_shouldChangeForkChoiceForLatestValidHashOnInvalidExecutionPayload() {
    doMerge();
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.ACCEPTED);
    final UInt64 slotToImport = prepFinalizeEpoch(2);
    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    // Make an optimistic chain
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
    final SignedBlockAndState maybeValidBlock = chainBuilder.generateNextBlock();
    storageSystem.chainUpdater().setCurrentSlot(maybeValidBlock.getSlot());
    importBlockOptimistically(maybeValidBlock);

    storageSystem.chainUpdater().setCurrentSlot(maybeValidBlock.getSlot().increment());
    importBlockOptimistically(chainBuilder.generateNextBlock());

    final SignedBlockAndState latestOptimisticBlock = chainBuilder.generateNextBlock();
    storageSystem.chainUpdater().setCurrentSlot(latestOptimisticBlock.getSlot());
    importBlockOptimistically(latestOptimisticBlock);
    assertHeadIsOptimistic(latestOptimisticBlock);
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    assertThat(forkChoiceStrategy.getChainHeads().get(0).getRoot())
        .isEqualTo(latestOptimisticBlock.getRoot());

    // make EL returning INVALID with latestValidHash in the past (maybeValidBlock)
    executionLayer.setPayloadStatus(
        PayloadStatus.invalid(
            Optional.of(maybeValidBlock.getExecutionBlockHash().get()), Optional.empty()));
    storageSystem.chainUpdater().setCurrentSlot(latestOptimisticBlock.getSlot().increment());
    SignedBlockAndState invalidBlock = chainBuilder.generateNextBlock();
    importBlockWithError(invalidBlock, FailureReason.FAILED_STATE_TRANSITION);
    assertThat(forkChoice.processHead(invalidBlock.getSlot())).isCompleted();

    assertHeadIsOptimistic(maybeValidBlock);
    assertThat(forkChoiceStrategy.getChainHeads().get(0).getRoot())
        .isEqualTo(maybeValidBlock.getRoot());
  }

  @Test
  void onBlock_shouldNotifyOptimisticSyncChangeOnlyWhenImportingOnCanonicalHead() {
    doMerge();
    UInt64 slotToImport = prepFinalizeEpoch(2);

    // since protoArray initializes with optimistic nodes,
    // we expect a first notification to be optimistic false
    verify(optimisticSyncStateTracker).onOptimisticHeadChanged(false);

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    slotToImport = prepFinalizeEpoch(4);

    // make EL returning SYNCING
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);

    // generate block which finalize epoch 4
    final SignedBlockAndState epoch6Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlockOptimistically(epoch6Block);

    verify(optimisticSyncStateTracker).onOptimisticHeadChanged(true);

    UInt64 forkSlot = slotToImport.increment();

    storageSystem.chainUpdater().setCurrentSlot(forkSlot);

    ChainBuilder alternativeChain = chainBuilder.fork();

    // make EL returning SYNCING
    executionLayer.setPayloadStatus(PayloadStatus.VALID);

    importBlock(chainBuilder.generateBlockAtSlot(forkSlot));

    verify(optimisticSyncStateTracker, times(2)).onOptimisticHeadChanged(false);

    // builds atop the canonical chain
    storageSystem.chainUpdater().setCurrentSlot(forkSlot.plus(1));
    importBlock(chainBuilder.generateBlockAtSlot(forkSlot.plus(1)));

    processHead(forkSlot.plus(1));

    // make EL returning SYNCING
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);

    // import a fork which won't be canonical
    importBlockOptimistically(alternativeChain.generateBlockAtSlot(forkSlot));

    // no notification is expected
    verifyNoMoreInteractions(optimisticSyncStateTracker);
  }

  @Test
  void applyHead_shouldSendForkChoiceUpdatedNotification() {
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().advanceChainUntil(1);

    processHead(ONE);

    assertForkChoiceUpdateNotification(blockAndState, false);
  }

  @Test
  void applyHead_shouldSendForkChoiceUpdatedNotificationWhenOptimistic() {
    doMerge();
    finalizeEpoch(2);
    assertThat(recentChainData.isChainHeadOptimistic()).isFalse();

    final UInt64 nextBlockSlot = storageSystem.chainBuilder().getLatestSlot().plus(1);
    storageSystem.chainUpdater().setCurrentSlot(nextBlockSlot);
    final SignedBlockAndState blockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextBlockSlot);

    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);
    importBlockOptimistically(blockAndState);
    assertForkChoiceUpdateNotification(blockAndState, true);

    // Optimistic head should be tracked
    assertHeadIsOptimistic(blockAndState);

    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);
    processHead(recentChainData.getHeadSlot());

    assertForkChoiceUpdateNotification(blockAndState, true, times(2));
  }

  @Test
  void processHead_shouldValidateAncestorTransitionBlockWhenHeadNowValid() {
    doMerge();

    assertThat(forkChoice.processHead(recentChainData.getHeadSlot())).isCompleted();

    verify(transitionBlockValidator)
        .verifyAncestorTransitionBlock(recentChainData.getBestBlockRoot().orElseThrow());
  }

  @Test
  void processHead_shouldMarkHeadInvalidAndRunForkChoiceWhenTransitionBlockFoundToBeInvalid() {
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);

    Bytes32 initialHeadRoot = recentChainData.getChainHead().orElseThrow().getRoot();

    doMerge(true);

    assertThat(recentChainData.isChainHeadOptimistic()).isTrue();

    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.VALID);
    final Bytes32 chainHeadRoot = recentChainData.getChainHead().orElseThrow().getRoot();
    when(transitionBlockValidator.verifyAncestorTransitionBlock(chainHeadRoot))
        .thenReturn(
            SafeFuture.completedFuture(
                new PayloadValidationResult(
                    PayloadStatus.invalid(Optional.empty(), Optional.empty()))));

    assertThat(recentChainData.getStore().containsBlock(chainHeadRoot)).isTrue();

    UInt64 headSlot = recentChainData.getHeadSlot();
    assertThat(forkChoice.processHead(headSlot)).isCompleted();

    // Chain head was marked invalid so removed from the store
    assertThat(recentChainData.getStore().containsBlock(chainHeadRoot)).isFalse();
    // Chain head reverted to the previous valid head
    assertThat(recentChainData.getChainHead().map(ChainHead::getRoot)).hasValue(initialHeadRoot);

    ArgumentCaptor<ForkChoiceState> forkChoiceStateCaptor =
        ArgumentCaptor.forClass(ForkChoiceState.class);

    verify(forkChoiceNotifier, times(2))
        .onForkChoiceUpdated(forkChoiceStateCaptor.capture(), eq(Optional.empty()));

    // EL should have been notified of the invalid head first and after that the valid
    // head
    List<ForkChoiceState> notifiedStates = forkChoiceStateCaptor.getAllValues();
    assertThat(notifiedStates.get(0).getHeadBlockRoot()).isEqualTo(chainHeadRoot);
    assertThat(notifiedStates.get(1).getHeadBlockRoot()).isEqualTo(initialHeadRoot);
  }

  @Test
  void processHead_shouldNotValidateAncestorTransitionBlockWhenHeadNotValid() {
    doMerge();
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);

    assertThat(forkChoice.processHead(recentChainData.getHeadSlot())).isCompleted();

    verifyNoInteractions(transitionBlockValidator);
  }

  @Test
  void onBlock_shouldUseLatestValidHashFromForkChoiceUpdated() {
    doMerge();
    finalizeEpoch(2);
    assertThat(recentChainData.isChainHeadOptimistic()).isFalse();

    final UInt64 nextBlockSlot = storageSystem.chainBuilder().getLatestSlot().plus(1);
    storageSystem.chainUpdater().setCurrentSlot(nextBlockSlot);
    final SignedBlockAndState blockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextBlockSlot);

    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(blockAndState.getBlock(), Optional.empty(), executionLayer);
    assertBlockImportedSuccessfully(result, true);

    assertForkChoiceUpdateNotification(blockAndState, true);

    // let's import a new block on top the optimistic head
    // let's make the EL return ACCEPTED on newPayload call but return INVALID on forkChoiceUpdated
    // call
    // INVALID will give us a lastValidHash corresponding to the previous block payload (imported
    // optimistically)

    executionLayer.setPayloadStatus(PayloadStatus.ACCEPTED);

    PayloadStatus invalidWithLastValidBlockHash =
        PayloadStatus.create(
            ExecutionPayloadStatus.INVALID,
            blockAndState
                .getBlock()
                .getMessage()
                .getBody()
                .getOptionalExecutionPayload()
                .map(ExecutionPayload::getBlockHash),
            Optional.empty());

    // first time, fork choice update block will be invalid and after that it will be valid
    setForkChoiceNotifierConsecutiveForkChoiceUpdatedResults(
        List.of(invalidWithLastValidBlockHash, PayloadStatus.VALID));

    storageSystem.chainUpdater().setCurrentSlot(nextBlockSlot.increment());
    final SignedBlockAndState blockAndStatePlus1 =
        storageSystem.chainBuilder().generateBlockAtSlot(nextBlockSlot.increment());

    // before importing, previous block is optimistic
    assertThat(isFullyValidated(blockAndState.getRoot())).isFalse();

    importBlockOptimistically(blockAndStatePlus1);

    // after importing, previous block is fully valid
    assertThat(isFullyValidated(blockAndState.getRoot())).isTrue();

    // running fork choice will be automatic because of the invalid head block, no need of manual
    // head processing in the test

    ArgumentCaptor<ForkChoiceState> forkChoiceStateCaptor =
        ArgumentCaptor.forClass(ForkChoiceState.class);
    verify(forkChoiceNotifier, atLeastOnce())
        .onForkChoiceUpdated(forkChoiceStateCaptor.capture(), eq(Optional.empty()));

    // last notification to EL should be a valid block
    ForkChoiceState lastNotifiedState = forkChoiceStateCaptor.getValue();
    assertThat(lastNotifiedState.getHeadBlockRoot()).isEqualTo(blockAndState.getRoot());

    // New head is optimistic because latestValidHash might still point to an optimistic block
    assertHeadIsOptimistic(blockAndState);
  }

  @ParameterizedTest(name = "onForkChoiceUpdatedResult_{0}")
  @MethodSource("getForkChoiceUpdatedResults")
  void onForkChoiceUpdatedResult_shouldLogWhenInvalidTerminalBlock(
      final ForkChoiceUpdatedResult result) {
    final ForkChoiceState state = mock(ForkChoiceState.class);
    when(state.getHeadExecutionBlockHash()).thenReturn(Bytes32.random());
    when(state.getHeadBlockRoot()).thenReturn(Bytes32.random());
    try (LogCaptor logCaptor = LogCaptor.forClass(ForkChoice.class)) {
      forkChoice.onForkChoiceUpdatedResult(
          new ForkChoiceUpdatedResultNotification(
              state, true, SafeFuture.completedFuture(Optional.ofNullable(result))));
      if (result.getPayloadStatus().hasInvalidStatus()) {
        final List<String> errorLogs = logCaptor.getErrorLogs();
        assertThat(errorLogs).singleElement().asString().contains("INVALID", "terminal");
      } else {
        assertThat(logCaptor.getErrorLogs()).isEmpty();
      }
    }
  }

  @Test
  void prepareForBlockProduction_NotYetInProposalSlotShouldRunOnTickWhenWithinTolerance() {
    final UInt64 newTime =
        spec.getSlotStartTimeMillis(ONE, recentChainData.getGenesisTimeMillis())
            .minusMinZero(BLOCK_CREATION_TOLERANCE_MS - 100);
    storageSystem.chainUpdater().setTimeMillis(newTime);

    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ZERO));
    assertThat(forkChoice.prepareForBlockProduction(ONE)).isCompleted();
    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ONE));

    verify(forkChoiceNotifier, times(1)).onForkChoiceUpdated(any(), eq(Optional.of(ONE)));
  }

  @Test
  void prepareForBlockProduction_NotYetInProposalSlotShouldNotRunOnTickWhenOutOfTolerance() {
    final UInt64 newTime =
        spec.getSlotStartTimeMillis(ONE, recentChainData.getGenesisTimeMillis())
            .minusMinZero(BLOCK_CREATION_TOLERANCE_MS + 100);
    storageSystem.chainUpdater().setTimeMillis(newTime);

    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ZERO));
    assertThat(forkChoice.prepareForBlockProduction(ONE)).isCompleted();
    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ZERO));

    verifyNoInteractions(forkChoiceNotifier);
  }

  private static Stream<ForkChoiceUpdatedResult> getForkChoiceUpdatedResults() {
    Set<PayloadStatus> statuses =
        Set.of(
            PayloadStatus.invalid(Optional.empty(), Optional.empty()),
            PayloadStatus.invalid(Optional.empty(), Optional.of("error")),
            PayloadStatus.invalid(Optional.of(Bytes32.random()), Optional.empty()),
            PayloadStatus.failedExecution(new RuntimeException("error")),
            PayloadStatus.ACCEPTED,
            PayloadStatus.SYNCING,
            PayloadStatus.VALID);
    return statuses.stream().map(status -> new ForkChoiceUpdatedResult(status, Optional.empty()));
  }

  private void assertHeadIsOptimistic(final SignedBlockAndState blockAndState) {
    final ChainHead optimisticHead = recentChainData.getChainHead().orElseThrow();
    assertThat(optimisticHead.getSlot()).isEqualTo(blockAndState.getSlot());
    assertThat(optimisticHead.getRoot()).isEqualTo(blockAndState.getRoot());
    assertThat(optimisticHead.isOptimistic()).isTrue();
    assertThat(recentChainData.isChainHeadOptimistic()).isTrue();
  }

  private boolean isFullyValidated(final Bytes32 root) {
    return recentChainData.getForkChoiceStrategy().orElseThrow().isFullyValidated(root);
  }

  private void assertForkChoiceUpdateNotification(
      final SignedBlockAndState blockAndState,
      final boolean optimisticHead,
      final VerificationMode mode) {
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    final Bytes32 headExecutionHash =
        forkChoiceStrategy.executionBlockHash(blockAndState.getRoot()).orElseThrow();
    final Bytes32 justifiedExecutionHash =
        forkChoiceStrategy
            .executionBlockHash(recentChainData.getJustifiedCheckpoint().orElseThrow().getRoot())
            .orElseThrow();
    final Bytes32 finalizedExecutionHash =
        forkChoiceStrategy
            .executionBlockHash(recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot())
            .orElseThrow();
    verify(forkChoiceNotifier, mode)
        .onForkChoiceUpdated(
            new ForkChoiceState(
                blockAndState.getRoot(),
                blockAndState.getSlot(),
                headExecutionHash,
                justifiedExecutionHash,
                finalizedExecutionHash,
                optimisticHead),
            Optional.empty());
  }

  private void assertForkChoiceUpdateNotification(
      final SignedBlockAndState blockAndState, final boolean optimisticHead) {
    assertForkChoiceUpdateNotification(blockAndState, optimisticHead, times(1));
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

    justifyEpoch(chainUpdater, epoch);

    prepEpochForJustification(chainUpdater, epochPlus2StartSlot);

    return epochPlus2StartSlot;
  }

  private void finalizeEpoch(final long epoch) {
    final UInt64 nextBlockSlot = prepFinalizeEpoch(epoch);
    importBlock(chainBuilder.generateBlockAtSlot(nextBlockSlot));
  }

  private void doMerge() {
    doMerge(false);
  }

  private void doMerge(final boolean optimistic) {
    final SignedBlockAndState epoch4Block = generateMergeBlock();

    if (optimistic) {
      storageSystem.chainUpdater().saveOptimisticBlock(epoch4Block);
    } else {
      storageSystem.chainUpdater().saveBlock(epoch4Block);
    }
    storageSystem.chainUpdater().updateBestBlock(epoch4Block);
  }

  private SignedBlockAndState generateMergeBlock() {
    final UInt256 terminalTotalDifficulty =
        spec.getGenesisSpecConfig().toVersionBellatrix().orElseThrow().getTerminalTotalDifficulty();
    final Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 terminalBlockParentHash = dataStructureUtil.randomBytes32();
    final PowBlock terminalBlock =
        new PowBlock(
            terminalBlockHash, terminalBlockParentHash, terminalTotalDifficulty.plus(1), ZERO);
    final PowBlock terminalParentBlock =
        new PowBlock(
            terminalBlockParentHash,
            dataStructureUtil.randomBytes32(),
            terminalTotalDifficulty.subtract(1),
            ZERO);
    executionLayer.addPowBlock(terminalBlock);
    executionLayer.addPowBlock(terminalParentBlock);
    final SignedBlockAndState epoch4Block =
        chainBuilder.generateBlockAtSlot(
            storageSystem.chainUpdater().getHeadSlot().plus(1),
            BlockOptions.create().setTerminalBlockHash(terminalBlockHash));
    return epoch4Block;
  }

  @Test
  void onAttestation_shouldBeInvalidWhenTargetRefersToBlockAfterTargetEpochStart() {
    final SignedBlockAndState targetBlock = chainBuilder.generateBlockAtSlot(5);
    importBlock(targetBlock);

    // Attestation where the target checkpoint has a slot prior to the block it references
    final Checkpoint targetCheckpoint = new Checkpoint(ZERO, targetBlock.getRoot());
    final Attestation attestation =
        attestationSchema.create(
            attestationSchema.getAggregationBitsSchema().ofBits(5),
            new AttestationData(
                targetBlock.getSlot(),
                spec.computeEpochAtSlot(targetBlock.getSlot()),
                targetBlock.getRoot(),
                targetBlock.getState().getCurrentJustifiedCheckpoint(),
                targetCheckpoint),
            BLSSignature.empty());
    final SafeFuture<AttestationProcessingResult> result =
        forkChoice.onAttestation(ValidateableAttestation.from(spec, attestation));
    assertThat(result)
        .isCompletedWithValue(
            AttestationProcessingResult.invalid(
                "LMD vote must be consistent with FFG vote target"));
  }

  @Test
  void onAttestation_shouldDeferAttestationsFromCurrentSlot() {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(UInt64.valueOf(10));
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    final SignedBlockAndState targetBlock = chainBuilder.generateBlockAtSlot(currentSlot);
    importBlock(targetBlock);

    final Attestation attestation =
        chainBuilder.streamValidAttestationsWithTargetBlock(targetBlock).findFirst().orElseThrow();
    final SafeFuture<AttestationProcessingResult> result =
        forkChoice.onAttestation(ValidateableAttestation.from(spec, attestation));
    assertThat(result).isCompletedWithValue(AttestationProcessingResult.DEFER_FOR_FORK_CHOICE);

    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    assertThat(forkChoiceStrategy.getWeight(targetBlock.getRoot())).contains(ZERO);

    // Should apply at start of next slot.
    forkChoice.onTick(
        spec.getSlotStartTimeMillis(currentSlot.plus(1), recentChainData.getGenesisTimeMillis()),
        Optional.empty());
    processHead(currentSlot.plus(1));

    assertThat(forkChoiceStrategy.getWeight(targetBlock.getRoot()).orElseThrow())
        .isGreaterThan(ZERO);
  }

  private UInt64 applyAttestationFromValidator(
      final UInt64 validatorIndex, final SignedBlockAndState targetBlock) {
    // Note this attestation is wildly invalid but we're going to shove it straight into fork choice
    // as pre-validated.
    final UInt64 updatedAttestationSlot = UInt64.valueOf(20);
    final ValidateableAttestation updatedVote =
        ValidateableAttestation.from(
            spec,
            attestationSchema.create(
                attestationSchema.getAggregationBitsSchema().ofBits(16),
                new AttestationData(
                    updatedAttestationSlot,
                    UInt64.ONE,
                    targetBlock.getRoot(),
                    recentChainData.getStore().getJustifiedCheckpoint(),
                    new Checkpoint(
                        spec.computeEpochAtSlot(updatedAttestationSlot), targetBlock.getRoot())),
                dataStructureUtil.randomSignature()));
    final IndexedAttestationSchema indexedAttestationSchema =
        spec.atSlot(updatedAttestationSlot).getSchemaDefinitions().getIndexedAttestationSchema();
    updatedVote.setIndexedAttestation(
        indexedAttestationSchema.create(
            indexedAttestationSchema.getAttestingIndicesSchema().of(validatorIndex),
            updatedVote.getData(),
            updatedVote.getAttestation().getAggregateSignature()));

    forkChoice.applyIndexedAttestations(List.of(updatedVote));
    return updatedAttestationSlot;
  }

  private void assertBlockImportedSuccessfully(
      final SafeFuture<BlockImportResult> importResult, final boolean optimistically) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = importResult.join();
    assertThat(result.isSuccessful()).describedAs(result.toString()).isTrue();
    assertThat(result.isImportedOptimistically())
        .describedAs(result.toString())
        .isEqualTo(optimistically);
  }

  private void importBlock(final SignedBlockAndState block) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block.getBlock(), Optional.empty(), executionLayer);
    assertBlockImportedSuccessfully(result, false);
  }

  private void importBlockOptimistically(final SignedBlockAndState block) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block.getBlock(), Optional.empty(), executionLayer);
    assertBlockImportedSuccessfully(result, true);
  }

  private void assertBlockImportFailure(
      final SafeFuture<BlockImportResult> importResult, FailureReason failureReason) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = importResult.join();
    assertThat(result.getFailureReason()).isEqualTo(failureReason);
  }

  private void importBlockWithError(final SignedBlockAndState block, FailureReason failureReason) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block.getBlock(), Optional.empty(), executionLayer);
    assertBlockImportFailure(result, failureReason);
  }

  private void processHead(final UInt64 slot) {
    assertThat(forkChoice.processHead(slot)).isCompleted();
  }

  private void setForkChoiceNotifierForkChoiceUpdatedResult(final PayloadStatus status) {
    setForkChoiceNotifierConsecutiveForkChoiceUpdatedResults(List.of(status));
  }

  private void setForkChoiceNotifierConsecutiveForkChoiceUpdatedResults(
      final List<PayloadStatus> statuses) {
    if (statuses.isEmpty()) {
      return;
    }
    Stubber stubber = null;
    for (PayloadStatus status : statuses) {
      ForkChoiceUpdatedResult result =
          Optional.ofNullable(status)
              .map(payloadStatus -> new ForkChoiceUpdatedResult(payloadStatus, Optional.empty()))
              .orElse(null);
      Answer<Void> onForkChoiceUpdatedResultAnswer = getOnForkChoiceUpdatedResultAnswer(result);
      if (stubber == null) {
        stubber = doAnswer(onForkChoiceUpdatedResultAnswer);
      } else {
        stubber.doAnswer(onForkChoiceUpdatedResultAnswer);
      }
    }
    stubber.when(forkChoiceNotifier).onForkChoiceUpdated(any(), any());
  }

  private Answer<Void> getOnForkChoiceUpdatedResultAnswer(ForkChoiceUpdatedResult result) {
    return invocation -> {
      forkChoice.onForkChoiceUpdatedResult(
          new ForkChoiceUpdatedResultNotification(
              invocation.getArgument(0),
              false,
              SafeFuture.completedFuture(Optional.ofNullable(result))));
      return null;
    };
  }
}
