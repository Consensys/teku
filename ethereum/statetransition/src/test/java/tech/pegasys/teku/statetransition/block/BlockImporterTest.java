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

package tech.pegasys.teku.statetransition.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.blobs.BlobsManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.StubForkChoiceNotifier;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class BlockImporterTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final SpecConfig genesisConfig = spec.getGenesisSpecConfig();
  private final AttestationSchema attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(8);
  private final BlockImportNotifications blockImportNotifications =
      mock(BlockImportNotifications.class);
  private final RecentChainData recentChainData =
      MemoryOnlyRecentChainData.builder().specProvider(spec).build();
  private final WeakSubjectivityValidator weakSubjectivityValidator =
      mock(WeakSubjectivityValidator.class);
  private final ForkChoiceNotifier forkChoiceNotifier = new StubForkChoiceNotifier();
  private final MergeTransitionBlockValidator transitionBlockValidator =
      new MergeTransitionBlockValidator(spec, recentChainData, ExecutionLayerChannel.NOOP);
  private final ForkChoice forkChoice =
      new ForkChoice(
          spec,
          new InlineEventThread(),
          recentChainData,
          BlobsManager.NOOP,
          forkChoiceNotifier,
          transitionBlockValidator);
  private final BeaconChainUtil localChain =
      BeaconChainUtil.create(spec, recentChainData, validatorKeys, forkChoice, false);

  private final RecentChainData otherStorage = MemoryOnlyRecentChainData.create();
  private final BeaconChainUtil otherChain =
      BeaconChainUtil.create(otherStorage, validatorKeys, false);

  private final BlockImporter blockImporter =
      new BlockImporter(
          spec,
          blockImportNotifications,
          recentChainData,
          forkChoice,
          weakSubjectivityValidator,
          ExecutionLayerChannel.NOOP);

  @BeforeAll
  public static void init() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void dispose() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @BeforeEach
  public void setup() {
    otherChain.initializeStorage();
    localChain.initializeStorage();
    when(weakSubjectivityValidator.isBlockValid(any(), any())).thenReturn(true);
  }

  @Test
  public void importBlock_success() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UInt64.ONE);
    localChain.setSlot(block.getSlot());

    final BlockImportResult result = blockImporter.importBlock(block).get();
    assertWeakSubjectivityWasChecked();
    assertSuccessfulResult(result);
  }

  @Test
  public void importBlock_errorDuringWeakSubjectivityCheck() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UInt64.ONE);
    localChain.setSlot(block.getSlot());
    doThrow(new RuntimeException("oops"))
        .when(weakSubjectivityValidator)
        .validateLatestFinalizedCheckpoint(any(), any());

    final BlockImportResult result = blockImporter.importBlock(block).get();
    assertWeakSubjectivityWasChecked();
    assertImportFailed(result, FailureReason.INTERNAL_ERROR);
  }

  @Test
  public void importBlock_alreadyInChain() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UInt64.ONE);
    localChain.setSlot(block.getSlot());

    assertThat(blockImporter.importBlock(block).get().isSuccessful()).isTrue();
    BlockImportResult result = blockImporter.importBlock(block).get();
    assertSuccessfulResult(result);
  }

  @Test
  public void importBlock_validAttestations() throws Exception {

    UInt64 currentSlot = UInt64.ONE;
    SignedBeaconBlock block1 = localChain.createAndImportBlockAtSlot(currentSlot);
    currentSlot = currentSlot.plus(UInt64.ONE);

    AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary stateAndBlock =
        recentChainData
            .getStore()
            .retrieveStateAndBlockSummary(block1.getRoot())
            .join()
            .orElseThrow();
    List<Attestation> attestations =
        attestationGenerator.getAttestationsForSlot(stateAndBlock, currentSlot);
    List<Attestation> aggregatedAttestations =
        AttestationGenerator.groupAndAggregateAttestations(attestations);

    currentSlot = currentSlot.plus(UInt64.ONE);

    localChain.createAndImportBlockAtSlotWithAttestations(currentSlot, aggregatedAttestations);
  }

  @Test
  public void importBlock_attestationWithInvalidSignature() throws Exception {

    UInt64 currentSlot = UInt64.ONE;
    SignedBeaconBlock block1 = localChain.createAndImportBlockAtSlot(currentSlot);
    currentSlot = currentSlot.plus(UInt64.ONE);

    AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary stateAndBlock =
        recentChainData
            .getStore()
            .retrieveStateAndBlockSummary(block1.getRoot())
            .join()
            .orElseThrow();
    List<Attestation> attestations =
        attestationGenerator.getAttestationsForSlot(stateAndBlock, currentSlot);
    List<Attestation> aggregatedAttestations =
        AttestationGenerator.groupAndAggregateAttestations(attestations);

    // make one attestation signature invalid
    int invalidAttIdx = aggregatedAttestations.size() / 2;
    Attestation att = aggregatedAttestations.get(invalidAttIdx);
    Attestation invalidAtt =
        attestationSchema.create(
            att.getAggregationBits(), att.getData(), BLSTestUtil.randomSignature(1));
    aggregatedAttestations.set(invalidAttIdx, invalidAtt);

    UInt64 currentSlotFinal = currentSlot.plus(UInt64.ONE);

    assertThatCode(
            () -> {
              localChain.createAndImportBlockAtSlotWithAttestations(
                  currentSlotFinal, aggregatedAttestations);
            })
        .hasMessageContaining("signature");
  }

  @Test
  public void importBlock_latestFinalizedBlock() throws Exception {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    UInt64 currentSlot = recentChainData.getHeadSlot();
    for (int i = 0; i < genesisConfig.getSlotsPerEpoch(); i++) {
      currentSlot = currentSlot.plus(UInt64.ONE);
      final SignedBeaconBlock block = localChain.createAndImportBlockAtSlot(currentSlot);
      blocks.add(block);
    }

    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 bestRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 bestEpoch = spec.computeEpochAtSlot(recentChainData.getHeadSlot());
    assertThat(bestEpoch).isEqualTo(SpecConfig.GENESIS_EPOCH.plus(1));
    final Checkpoint finalized = new Checkpoint(bestEpoch, bestRoot);
    tx.setFinalizedCheckpoint(finalized, false);
    tx.commit().join();

    // Known blocks should report as successfully imported
    final BlockImportResult result = blockImporter.importBlock(blocks.get(blocks.size() - 1)).get();
    assertSuccessfulResult(result);
  }

  @Test
  public void importBlock_knownBlockOlderThanLatestFinalized() throws Exception {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    UInt64 currentSlot = recentChainData.getHeadSlot();
    for (int i = 0; i < genesisConfig.getSlotsPerEpoch(); i++) {
      currentSlot = currentSlot.plus(UInt64.ONE);
      final SignedBeaconBlock block = localChain.createAndImportBlockAtSlot(currentSlot);
      blocks.add(block);
    }

    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 bestRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 bestEpoch = spec.computeEpochAtSlot(recentChainData.getHeadSlot());
    assertThat(bestEpoch).isEqualTo(SpecConfig.GENESIS_EPOCH.plus(1));
    final Checkpoint finalized = new Checkpoint(bestEpoch, bestRoot);
    tx.setFinalizedCheckpoint(finalized, false);
    tx.commit().join();

    // Import a block prior to the latest finalized block
    final BlockImportResult result = blockImporter.importBlock(blocks.get(1)).get();
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_parentBlockFromSameSlot() throws Exception {
    // First import a valid block at slot 1
    final SignedBeaconBlock block = otherChain.createAndImportBlockAtSlot(UInt64.ONE);
    localChain.setSlot(block.getSlot());
    assertSuccessfulResult(blockImporter.importBlock(block).get());

    // Now create an alternate block 1 with the real block one as the parent block
    final BeaconBlock invalidAncestryUnsignedBlock =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            block.getSlot(),
            block.getMessage().getProposerIndex(),
            block.getMessage().hashTreeRoot(),
            block.getMessage().getStateRoot(),
            block.getMessage().getBody());
    final Signer signer = localChain.getSigner(block.getMessage().getProposerIndex().intValue());
    final SignedBeaconBlock invalidAncestryBlock =
        SignedBeaconBlock.create(
            spec,
            invalidAncestryUnsignedBlock,
            signer
                .signBlock(
                    invalidAncestryUnsignedBlock, otherStorage.getCurrentForkInfo().orElseThrow())
                .join());

    final BlockImportResult result = blockImporter.importBlock(invalidAncestryBlock).get();
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFailureReason())
        .isEqualTo(BlockImportResult.FAILED_INVALID_ANCESTRY.getFailureReason());
  }

  private void assertSuccessfulResult(final BlockImportResult result) {
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFailureReason()).isNull();
    assertThat(result.getFailureCause()).isEmpty();
  }

  @Test
  public void importBlock_fromFuture() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UInt64.ONE);

    final BlockImportResult result = blockImporter.importBlock(block).get();
    assertImportFailed(result, FailureReason.BLOCK_IS_FROM_FUTURE);
  }

  @Test
  public void importBlock_unknownParent() throws Exception {
    otherChain.createAndImportBlockAtSlot(UInt64.ONE);
    final SignedBeaconBlock block2 = otherChain.createAndImportBlockAtSlot(UInt64.valueOf(2));
    localChain.setSlot(block2.getSlot());

    final BlockImportResult result = blockImporter.importBlock(block2).get();
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_wrongChain() throws Exception {
    UInt64 currentSlot = recentChainData.getHeadSlot();
    for (int i = 0; i < 3; i++) {
      currentSlot = currentSlot.plus(UInt64.ONE);
      localChain.createAndImportBlockAtSlot(currentSlot);
    }
    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 finalizedRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 finalizedEpoch = UInt64.ONE;
    final Checkpoint finalized = new Checkpoint(finalizedEpoch, finalizedRoot);
    tx.setFinalizedCheckpoint(finalized, false);
    tx.commit().join();

    // Now create a new block that is not descendant from the finalized block
    AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary blockAndState =
        safeJoin(otherStorage.getChainHead().orElseThrow().asStateAndBlockSummary());
    final Attestation attestation = attestationGenerator.validAttestation(blockAndState);
    final SignedBeaconBlock block =
        otherChain.createAndImportBlockAtSlotWithAttestations(currentSlot, List.of(attestation));

    final BlockImportResult result = blockImporter.importBlock(block).get();
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_invalidStateTransition() throws Exception {
    final SignedBeaconBlock block = otherChain.createBlockAtSlot(UInt64.ONE);
    SignedBeaconBlock newBlock =
        SignedBeaconBlock.create(
            spec, block.getMessage().withStateRoot(Bytes32.ZERO), block.getSignature());
    localChain.setSlot(block.getSlot());

    final BlockImportResult result = blockImporter.importBlock(newBlock).get();
    assertImportFailed(result, FailureReason.FAILED_STATE_TRANSITION);
  }

  @Test
  public void importBlock_weakSubjectivityFailure_wrongAncestor() throws Exception {
    final UInt64 wsEpoch = UInt64.valueOf(10);
    final UInt64 wsEpochSlot = this.spec.computeStartSlotAtEpoch(wsEpoch);
    final SignedBeaconBlock wsBlock = localChain.createBlockAtSlot(wsEpochSlot);
    final SignedBeaconBlock otherBlock = otherChain.createBlockAtSlot(wsEpochSlot.plus(1));

    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final Checkpoint wsCheckpoint = new Checkpoint(wsEpoch, wsBlock.getRoot());
    final WeakSubjectivityConfig wsConfig =
        WeakSubjectivityConfig.builder()
            .specProvider(spec)
            .weakSubjectivityCheckpoint(wsCheckpoint)
            .build();
    final WeakSubjectivityValidator weakSubjectivityValidator =
        WeakSubjectivityValidator.lenient(wsConfig);
    final BlockImporter blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            recentChainData,
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    final BlockImportResult result = blockImporter.importBlock(otherBlock).get();
    assertImportFailed(result, FailureReason.FAILED_WEAK_SUBJECTIVITY_CHECKS);
  }

  @Test
  public void importBlock_weakSubjectivityChecksPass() throws Exception {
    final UInt64 wsEpoch = UInt64.valueOf(10);
    final UInt64 wsEpochSlot = this.spec.computeStartSlotAtEpoch(wsEpoch);
    final SignedBeaconBlock wsBlock = localChain.createBlockAtSlot(wsEpochSlot);
    final SignedBeaconBlock nextBlock = localChain.createAndImportBlockAtSlot(wsEpochSlot.plus(1));
    localChain.setSlot(wsEpochSlot.plus(1));

    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final Checkpoint wsCheckpoint = new Checkpoint(wsEpoch, wsBlock.getRoot());
    final WeakSubjectivityConfig wsConfig =
        WeakSubjectivityConfig.builder()
            .specProvider(spec)
            .weakSubjectivityCheckpoint(wsCheckpoint)
            .build();
    final WeakSubjectivityValidator weakSubjectivityValidator =
        WeakSubjectivityValidator.lenient(wsConfig);
    final BlockImporter blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            recentChainData,
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    // Import wsBlock
    final BlockImportResult result = blockImporter.importBlock(wsBlock).get();
    assertSuccessfulResult(result);
    // Import next valid block
    final BlockImportResult result2 = blockImporter.importBlock(nextBlock).get();
    assertSuccessfulResult(result2);
  }

  @Test
  public void importBlock_runWSPChecks() throws Exception {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            BlobsManager.NOOP,
            forkChoiceNotifier,
            transitionBlockValidator);
    final BlockImporter blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    // The current slot is far ahead of the block being imported
    final UInt64 wsPeriod = UInt64.valueOf(10);
    when(weakSubjectivityValidator.getWSPeriod(any())).thenReturn(Optional.of(wsPeriod));
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(wsPeriod).plus(1);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    final SignedBlockAndState blockToImport = storageSystem.chainBuilder().generateBlockAtSlot(1);

    // Import wsBlock
    final BlockImportResult result = blockImporter.importBlock(blockToImport.getBlock()).get();
    assertSuccessfulResult(result);

    // Verify ws period checks were run
    final CheckpointState finalizedCheckpointState =
        CheckpointState.create(
            spec,
            new Checkpoint(UInt64.ZERO, genesis.getRoot()),
            genesis.getBlock(),
            genesis.getState());
    verify(weakSubjectivityValidator)
        .validateLatestFinalizedCheckpoint(finalizedCheckpointState, currentSlot);
  }

  @Test
  public void importBlock_nonFinalizingChain_runWSPChecks() throws Exception {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            BlobsManager.NOOP,
            forkChoiceNotifier,
            transitionBlockValidator);
    final BlockImporter blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    // Set current time to be several WSP's ahead of finalized checkpoint
    final UInt64 wsPeriod = UInt64.valueOf(10);
    final UInt64 wsPeriodInSlots = wsPeriod.times(genesisConfig.getSlotsPerEpoch());
    when(weakSubjectivityValidator.getWSPeriod(any())).thenReturn(Optional.of(wsPeriod));
    final UInt64 currentSlot = wsPeriodInSlots.times(3).plus(1);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    // Add a recent block
    final SignedBlockAndState recentBlock =
        storageSystem.chainUpdater().advanceChain(currentSlot.minus(wsPeriodInSlots).minus(1));
    storageSystem.chainUpdater().updateBestBlock(recentBlock);

    // Import block new block
    final SignedBlockAndState blockToImport =
        storageSystem.chainBuilder().generateBlockAtSlot(currentSlot);

    // Import wsBlock
    final BlockImportResult result = blockImporter.importBlock(blockToImport.getBlock()).get();
    assertSuccessfulResult(result);

    // Verify ws period checks were run
    final CheckpointState finalizedCheckpointState =
        CheckpointState.create(
            spec,
            new Checkpoint(UInt64.ZERO, genesis.getRoot()),
            genesis.getBlock(),
            genesis.getState());
    verify(weakSubjectivityValidator)
        .validateLatestFinalizedCheckpoint(finalizedCheckpointState, currentSlot);
  }

  @Test
  public void importBlock_nonFinalizingChain_skipWSPChecks() throws Exception {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    storageSystem.chainUpdater().initializeGenesis();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            BlobsManager.NOOP,
            forkChoiceNotifier,
            transitionBlockValidator);
    final BlockImporter blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    // Set current time to be several WSP's ahead of finalized checkpoint
    final UInt64 wsPeriod = UInt64.valueOf(10);
    final UInt64 wsPeriodInSlots = wsPeriod.times(genesisConfig.getSlotsPerEpoch());
    when(weakSubjectivityValidator.getWSPeriod(any())).thenReturn(Optional.of(wsPeriod));
    final UInt64 currentSlot = wsPeriodInSlots.times(3).plus(1);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    // Add a recent block
    final SignedBlockAndState recentBlock =
        storageSystem.chainUpdater().advanceChain(currentSlot.minus(wsPeriodInSlots));
    storageSystem.chainUpdater().updateBestBlock(recentBlock);

    // Import block new block
    final SignedBlockAndState blockToImport =
        storageSystem.chainBuilder().generateBlockAtSlot(currentSlot);

    // Import wsBlock
    final BlockImportResult result = blockImporter.importBlock(blockToImport.getBlock()).get();
    assertSuccessfulResult(result);

    // Verify ws period checks were skipped
    verify(weakSubjectivityValidator, never()).validateLatestFinalizedCheckpoint(any(), any());
  }

  @Test
  public void getLatestCheckpointState_initialCall() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            BlobsManager.NOOP,
            forkChoiceNotifier,
            transitionBlockValidator);
    final BlockImporter blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();

    SafeFuture<CheckpointState> result = blockImporter.getLatestCheckpointState();
    assertThat(result).isCompleted();
    assertThat(result.join().getRoot()).isEqualTo(genesis.getRoot());

    // Second call should be the same
    result = blockImporter.getLatestCheckpointState();
    assertThat(result).isCompleted();
    assertThat(result.join().getRoot()).isEqualTo(genesis.getRoot());
  }

  @Test
  public void getLatestCheckpointState_shouldPullUpdatedFinalizedCheckpoint() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            BlobsManager.NOOP,
            forkChoiceNotifier,
            transitionBlockValidator);
    final BlockImporter blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();

    // Pull genesis checkpoint
    SafeFuture<CheckpointState> result = blockImporter.getLatestCheckpointState();
    assertThat(result).isCompleted();
    assertThat(result.join().getRoot()).isEqualTo(genesis.getRoot());

    // Update latest finalized
    final UInt64 newFinalizedEpoch = UInt64.valueOf(2);
    final SignedBlockAndState newFinalizedBlock =
        storageSystem.chainUpdater().advanceChain(spec.computeStartSlotAtEpoch(newFinalizedEpoch));
    storageSystem.chainUpdater().finalizeEpoch(newFinalizedEpoch);

    // Second call should pull new finalized checkpoint
    result = blockImporter.getLatestCheckpointState();
    assertThat(result).isCompleted();
    assertThat(result.join().getEpoch()).isEqualTo(newFinalizedEpoch);
    assertThat(result.join().getRoot()).isEqualTo(newFinalizedBlock.getRoot());
  }

  private void assertImportFailed(
      final BlockImportResult result, final BlockImportResult.FailureReason expectedReason) {
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFailureReason()).isEqualTo(expectedReason);
  }

  private void assertWeakSubjectivityWasChecked() {
    SafeFuture<CheckpointState> finalizedCheckpoint =
        recentChainData.getStore().retrieveFinalizedCheckpointAndState();
    assertThat(finalizedCheckpoint).isCompleted();
    UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    verify(weakSubjectivityValidator)
        .validateLatestFinalizedCheckpoint(finalizedCheckpoint.join(), currentSlot);
  }
}
