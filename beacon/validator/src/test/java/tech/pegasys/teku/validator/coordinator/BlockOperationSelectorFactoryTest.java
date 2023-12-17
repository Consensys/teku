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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractSignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContents;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.BlsToExecutionChangesValidator.BlsToExecutionChangeInvalidReason;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.AttesterSlashingValidator.AttesterSlashingInvalidReason;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.ProposerSlashingValidator.ProposerSlashingInvalidReason;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.SimpleOperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SignedContributionAndProofValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.OperationValidator;

class BlockOperationSelectorFactoryTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
      slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @SuppressWarnings("unchecked")
  private final OperationValidator<AttesterSlashing> attesterSlashingValidator =
      mock(OperationValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationValidator<ProposerSlashing> proposerSlashingValidator =
      mock(OperationValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationValidator<SignedVoluntaryExit> voluntaryExitValidator =
      mock(OperationValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationValidator<SignedBlsToExecutionChange> blsToExecutionChangeValidator =
      mock(OperationValidator.class);

  private final SignedContributionAndProofValidator contributionValidator =
      mock(SignedContributionAndProofValidator.class);

  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final OperationPool<AttesterSlashing> attesterSlashingPool =
      new SimpleOperationPool<>(
          "attester_slashing",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema),
          attesterSlashingValidator,
          Comparator.<AttesterSlashing>comparingInt(
                  slashing -> slashing.getIntersectingValidatorIndices().size())
              .reversed());
  private final OperationPool<ProposerSlashing> proposerSlashingPool =
      new SimpleOperationPool<>(
          "proposer_slashing",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
          proposerSlashingValidator);
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool =
      new SimpleOperationPool<>(
          "voluntary_exit",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
          voluntaryExitValidator);

  private final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool =
      new SimpleOperationPool<>(
          "bls_to_execution_Change",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(
              s -> s.toVersionCapella().orElseThrow().getBlsToExecutionChangesSchema()),
          blsToExecutionChangeValidator);

  private final SyncCommitteeContributionPool contributionPool =
      new SyncCommitteeContributionPool(spec, contributionValidator);

  private final DepositProvider depositProvider = mock(DepositProvider.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final Bytes32 defaultGraffiti = dataStructureUtil.randomBytes32();
  private final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
  private final BLSSignature randaoReveal = dataStructureUtil.randomSignature();

  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final ExecutionLayerBlockProductionManager executionLayer =
      mock(ExecutionLayerBlockProductionManager.class);

  private final ExecutionPayload defaultExecutionPayload =
      SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
          .getExecutionPayloadSchema()
          .getDefault();

  private final ExecutionPayloadHeader executionPayloadHeaderOfDefaultPayload =
      SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
          .getExecutionPayloadHeaderSchema()
          .getHeaderOfDefaultPayload();

  private final CapturingBeaconBlockBodyBuilder bodyBuilder =
      new CapturingBeaconBlockBodyBuilder(false);

  private final BlockOperationSelectorFactory factory =
      new BlockOperationSelectorFactory(
          spec,
          attestationPool,
          attesterSlashingPool,
          proposerSlashingPool,
          voluntaryExitPool,
          blsToExecutionChangePool,
          contributionPool,
          depositProvider,
          eth1DataCache,
          defaultGraffiti,
          forkChoiceNotifier,
          executionLayer);

  @BeforeEach
  void setUp() {
    when(attestationPool.getAttestationsForBlock(any(), any(), any()))
        .thenReturn(
            beaconBlockSchemaSupplier.apply(UInt64.ZERO).getAttestationsSchema().getDefault());
    when(contributionValidator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(attesterSlashingValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(proposerSlashingValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(voluntaryExitValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(blsToExecutionChangeValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void shouldNotSelectOperationsWhenNoneAreAvailable() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.empty(),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);

    assertThat(bodyBuilder.proposerSlashings).isEmpty();
    assertThat(bodyBuilder.attesterSlashings).isEmpty();
    assertThat(bodyBuilder.voluntaryExits).isEmpty();
    assertThat(bodyBuilder.syncAggregate.getSyncCommitteeBits().getBitCount()).isZero();
    assertThat(bodyBuilder.syncAggregate.getSyncCommitteeSignature().getSignature().isInfinity())
        .isTrue();
    assertThat(bodyBuilder.blsToExecutionChanges).isEmpty();
  }

  @Test
  void shouldIncludeValidOperations() {
    final UInt64 slot = UInt64.valueOf(2);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final SignedVoluntaryExit voluntaryExit = dataStructureUtil.randomSignedVoluntaryExit();
    final ProposerSlashing proposerSlashing = dataStructureUtil.randomProposerSlashing();
    final AttesterSlashing attesterSlashing = dataStructureUtil.randomAttesterSlashing();
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(1, parentRoot);
    final SignedBlsToExecutionChange blsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    addToPool(voluntaryExitPool, voluntaryExit);
    addToPool(proposerSlashingPool, proposerSlashing);
    addToPool(attesterSlashingPool, attesterSlashing);
    assertThat(contributionPool.addLocal(contribution)).isCompletedWithValue(ACCEPT);
    addToPool(blsToExecutionChangePool, blsToExecutionChange);

    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            randaoReveal,
            Optional.empty(),
            Optional.empty(),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);

    assertThat(bodyBuilder.randaoReveal).isEqualTo(randaoReveal);
    assertThat(bodyBuilder.graffiti).isEqualTo(defaultGraffiti);
    assertThat(bodyBuilder.proposerSlashings).containsOnly(proposerSlashing);
    assertThat(bodyBuilder.attesterSlashings).containsOnly(attesterSlashing);
    assertThat(bodyBuilder.voluntaryExits).containsOnly(voluntaryExit);
    assertThat(bodyBuilder.syncAggregate)
        .isEqualTo(
            spec.getSyncCommitteeUtilRequired(slot)
                .createSyncAggregate(List.of(contribution.getMessage().getContribution())));
    assertThat(bodyBuilder.blsToExecutionChanges).containsOnly(blsToExecutionChange);
  }

  private <T extends SszData> void addToPool(final OperationPool<T> pool, final T operation) {
    assertThat(pool.addRemote(operation, Optional.empty())).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldNotIncludeInvalidOperations() {
    final UInt64 slot = UInt64.valueOf(2);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final SignedVoluntaryExit voluntaryExit1 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(60));
    final SignedVoluntaryExit voluntaryExit2 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(65));
    final SignedVoluntaryExit voluntaryExit3 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(69));
    final SignedVoluntaryExit voluntaryExit4 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(69));
    final ProposerSlashing proposerSlashing1 =
        dataStructureUtil.randomProposerSlashing(UInt64.ONE, UInt64.valueOf(60));
    final ProposerSlashing proposerSlashing2 =
        dataStructureUtil.randomProposerSlashing(UInt64.ONE, UInt64.valueOf(61));
    final AttesterSlashing attesterSlashing1 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(60), UInt64.valueOf(62));
    final AttesterSlashing attesterSlashing2 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(63), UInt64.valueOf(64));
    final AttesterSlashing attesterSlashing3 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(62));
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(1, parentRoot);
    final SignedBlsToExecutionChange blsToExecutionChange1 =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final SignedBlsToExecutionChange blsToExecutionChange2 =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    addToPool(voluntaryExitPool, voluntaryExit1);
    addToPool(voluntaryExitPool, voluntaryExit2);
    addToPool(voluntaryExitPool, voluntaryExit3);
    addToPool(voluntaryExitPool, voluntaryExit4);
    addToPool(proposerSlashingPool, proposerSlashing1);
    addToPool(proposerSlashingPool, proposerSlashing2);
    addToPool(attesterSlashingPool, attesterSlashing1);
    addToPool(attesterSlashingPool, attesterSlashing2);
    addToPool(attesterSlashingPool, attesterSlashing3);
    assertThat(contributionPool.addRemote(contribution, Optional.empty()))
        .isCompletedWithValue(ACCEPT);
    addToPool(blsToExecutionChangePool, blsToExecutionChange1);
    addToPool(blsToExecutionChangePool, blsToExecutionChange2);

    when(proposerSlashingValidator.validateForBlockInclusion(blockSlotState, proposerSlashing2))
        .thenReturn(Optional.of(ProposerSlashingInvalidReason.INVALID_SIGNATURE));
    when(voluntaryExitValidator.validateForBlockInclusion(blockSlotState, voluntaryExit2))
        .thenReturn(Optional.of(ExitInvalidReason.invalidSignature()));
    when(attesterSlashingValidator.validateForBlockInclusion(blockSlotState, attesterSlashing2))
        .thenReturn(Optional.of(AttesterSlashingInvalidReason.ATTESTATIONS_NOT_SLASHABLE));
    when(blsToExecutionChangeValidator.validateForBlockInclusion(
            blockSlotState, blsToExecutionChange2))
        .thenReturn(Optional.of(BlsToExecutionChangeInvalidReason.invalidValidatorIndex()));

    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            randaoReveal,
            Optional.empty(),
            Optional.empty(),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);

    assertThat(bodyBuilder.randaoReveal).isEqualTo(randaoReveal);
    assertThat(bodyBuilder.graffiti).isEqualTo(defaultGraffiti);
    assertThat(bodyBuilder.proposerSlashings).isEmpty();
    assertThat(bodyBuilder.attesterSlashings).containsOnly(attesterSlashing1);
    // Both exit 3 or 4 are valid, but you can't include two exits for the same validator
    assertThat(bodyBuilder.voluntaryExits).hasSize(1).containsAnyOf(voluntaryExit3, voluntaryExit4);
    assertThat(bodyBuilder.syncAggregate)
        .isEqualTo(
            spec.getSyncCommitteeUtilRequired(slot)
                .createSyncAggregate(List.of(contribution.getMessage().getContribution())));
    assertThat(bodyBuilder.blsToExecutionChanges).containsOnly(blsToExecutionChange1);
  }

  @Test
  void shouldIncludeDefaultExecutionPayload() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconStatePreMerge(slot);
    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.of(false),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);
    assertThat(bodyBuilder.executionPayload).isEqualTo(defaultExecutionPayload);
  }

  @Test
  void shouldIncludeExecutionPayloadHeaderOfDefaultPayload() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconStatePreMerge(slot);
    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.of(true),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);
    assertThat(bodyBuilder.executionPayloadHeader)
        .isEqualTo(executionPayloadHeaderOfDefaultPayload);
  }

  @Test
  void shouldIncludeNonDefaultExecutionPayload() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
    prepareBlockProductionWithPayload(
        randomExecutionPayload, executionPayloadContext, blockSlotState);

    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.of(false),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
  }

  @Test
  void shouldIncludeExecutionPayloadHeaderIfBlindedBlockRequested() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final ExecutionPayloadHeader randomExecutionPayloadHeader =
        dataStructureUtil.randomExecutionPayloadHeader();

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
    prepareBlockProductionWithPayloadHeader(
        randomExecutionPayloadHeader, executionPayloadContext, blockSlotState);

    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.of(true),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);

    assertThat(bodyBuilder.executionPayloadHeader).isEqualTo(randomExecutionPayloadHeader);
  }

  @Test
  void shouldIncludeExecutionPayloadIfNoBlindedBlockRequested() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
    prepareBlockProductionWithPayload(
        randomExecutionPayload, executionPayloadContext, blockSlotState);

    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.of(false),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
  }

  @Test
  void shouldUnblindSignedBlindedBeaconBlock() {
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final SignedBeaconBlock blindedSignedBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    final CapturingBeaconBlockUnblinder blockUnblinder =
        new CapturingBeaconBlockUnblinder(spec.getGenesisSchemaDefinitions(), blindedSignedBlock);

    when(executionLayer.getUnblindedPayload(blindedSignedBlock))
        .thenReturn(SafeFuture.completedFuture(randomExecutionPayload));

    factory.createBlockUnblinderSelector().accept(blockUnblinder);

    assertThat(blockUnblinder.executionPayload).isCompletedWithValue(randomExecutionPayload);
  }

  @Test
  void shouldIncludeKzgCommitmentsInBlock() {
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));

    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();

    prepareBlockAndBlobsProduction(
        randomExecutionPayload, executionPayloadContext, blockSlotState, blobsBundle);

    final CapturingBeaconBlockBodyBuilder bodyBuilder = new CapturingBeaconBlockBodyBuilder(true);

    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.of(false),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);

    assertThat(bodyBuilder.blobKzgCommitments)
        .map(SszKZGCommitment::getKZGCommitment)
        .hasSameElementsAs(blobsBundle.getCommitments());
  }

  @Test
  void shouldIncludeKzgCommitmentsInBlindedBlock() {
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final ExecutionPayloadHeader randomExecutionPayloadHeader =
        dataStructureUtil.randomExecutionPayloadHeader();

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));

    final SszList<SszKZGCommitment> blobKzgCommitments =
        dataStructureUtil.randomBlobKzgCommitments();

    prepareBlindedBlockAndBlobsProduction(
        randomExecutionPayloadHeader, executionPayloadContext, blockSlotState, blobKzgCommitments);

    final CapturingBeaconBlockBodyBuilder bodyBuilder = new CapturingBeaconBlockBodyBuilder(true);

    factory
        .createSelector(
            parentRoot,
            blockSlotState,
            dataStructureUtil.randomSignature(),
            Optional.empty(),
            Optional.empty(),
            BlockProductionPerformance.NOOP)
        .accept(bodyBuilder);

    assertThat(bodyBuilder.blobKzgCommitments).hasSameElementsAs(blobKzgCommitments);
  }

  @Test
  void shouldGetBlobsBundleForBlock() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock();

    final BlobsBundle expectedBlobsBundle = dataStructureUtil.randomBlobsBundle();

    // the BlobsBundle is stored in the ExecutionPayloadResult
    prepareCachedPayloadResult(
        block.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        dataStructureUtil.randomPayloadExecutionContext(false),
        expectedBlobsBundle);

    final BlobsBundle blobsBundle = safeJoin(factory.createBlobsBundleSelector().apply(block));

    assertThat(blobsBundle).isEqualTo(expectedBlobsBundle);
  }

  @Test
  void shouldCreateBlobSidecarsForBlockContents() {
    final SignedBlockContents signedBlockContents = dataStructureUtil.randomSignedBlockContents();

    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(spec.atSlot(signedBlockContents.getSlot()).miscHelpers());

    final List<BlobSidecar> blobSidecars =
        factory.createBlobSidecarsSelector().apply(signedBlockContents);

    final SszList<Blob> expectedBlobs = signedBlockContents.getBlobs().orElseThrow();
    final SszList<SszKZGProof> expectedProofs = signedBlockContents.getKzgProofs().orElseThrow();
    final SszList<SszKZGCommitment> expectedCommitments =
        signedBlockContents
            .getSignedBlock()
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow();

    assertThat(blobSidecars).hasSize(expectedBlobs.size());

    IntStream.range(0, blobSidecars.size())
        .forEach(
            index -> {
              final BlobSidecar blobSidecar = blobSidecars.get(index);
              assertThat(blobSidecar.getIndex()).isEqualTo(UInt64.valueOf(index));
              assertThat(blobSidecar.getSignedBeaconBlockHeader())
                  .isEqualTo(signedBlockContents.getSignedBlock().asHeader());
              assertThat(blobSidecar.getBlob()).isEqualTo(expectedBlobs.get(index));
              assertThat(blobSidecar.getSszKZGProof()).isEqualTo(expectedProofs.get(index));
              assertThat(blobSidecar.getSszKZGCommitment())
                  .isEqualTo(expectedCommitments.get(index));
              // verify the merkle proof
              assertThat(miscHelpersDeneb.verifyBlobSidecarMerkleProof(blobSidecar)).isTrue();
            });
  }

  @Test
  void shouldFailCreatingBlobSidecarsIfBuilderBlobsBundleIsNotConsistent() {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);

    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
        dataStructureUtil.randomBuilderBlobsBundle(3);

    prepareCachedBuilderPayload(
        signedBlindedBeaconBlock.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        Optional.of(blobsBundle));

    assertThatThrownBy(() -> factory.createBlobSidecarsSelector().apply(signedBlindedBeaconBlock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Commitments in the builder BlobsBundle don't match the commitments in the block");
  }

  @Test
  void shouldCreateBlobSidecarsForBlindedBlock() {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);

    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(spec.atSlot(signedBlindedBeaconBlock.getSlot()).miscHelpers());

    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
        dataStructureUtil.randomBuilderBlobsBundle(commitments);

    prepareCachedBuilderPayload(
        signedBlindedBeaconBlock.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        Optional.of(blobsBundle));

    final List<BlobSidecar> blobSidecars =
        factory.createBlobSidecarsSelector().apply(signedBlindedBeaconBlock);

    final SszList<Blob> expectedBlobs = blobsBundle.getBlobs();
    final SszList<SszKZGProof> expectedProofs = blobsBundle.getProofs();
    final SszList<SszKZGCommitment> expectedCommitments =
        signedBlindedBeaconBlock
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow();

    assertThat(blobSidecars).hasSize(expectedBlobs.size());

    IntStream.range(0, blobSidecars.size())
        .forEach(
            index -> {
              final BlobSidecar blobSidecar = blobSidecars.get(index);
              assertThat(blobSidecar.getIndex()).isEqualTo(UInt64.valueOf(index));
              assertThat(blobSidecar.getSignedBeaconBlockHeader())
                  .isEqualTo(signedBlindedBeaconBlock.asHeader());
              assertThat(blobSidecar.getBlob()).isEqualTo(expectedBlobs.get(index));
              assertThat(blobSidecar.getSszKZGProof()).isEqualTo(expectedProofs.get(index));
              assertThat(blobSidecar.getSszKZGCommitment())
                  .isEqualTo(expectedCommitments.get(index));
              // verify the merkle proof
              assertThat(miscHelpersDeneb.verifyBlobSidecarMerkleProof(blobSidecar)).isTrue();
            });
  }

  private void prepareBlockProductionWithPayload(
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState) {
    when(executionLayer.initiateBlockProduction(
            executionPayloadContext, blockSlotState, false, BlockProductionPerformance.NOOP))
        .thenReturn(
            new ExecutionPayloadResult(
                executionPayloadContext,
                Optional.of(SafeFuture.completedFuture(executionPayload)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
  }

  private void prepareBlockProductionWithPayloadHeader(
      final ExecutionPayloadHeader executionPayloadHeader,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState) {
    when(executionLayer.initiateBlockProduction(
            executionPayloadContext, blockSlotState, true, BlockProductionPerformance.NOOP))
        .thenReturn(
            new ExecutionPayloadResult(
                executionPayloadContext,
                Optional.empty(),
                Optional.empty(),
                Optional.of(
                    SafeFuture.completedFuture(
                        HeaderWithFallbackData.create(executionPayloadHeader))),
                Optional.empty()));
  }

  private void prepareBlockAndBlobsProduction(
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final BlobsBundle blobsBundle) {
    when(executionLayer.initiateBlockAndBlobsProduction(
            executionPayloadContext, blockSlotState, false, BlockProductionPerformance.NOOP))
        .thenReturn(
            new ExecutionPayloadResult(
                executionPayloadContext,
                Optional.of(SafeFuture.completedFuture(executionPayload)),
                Optional.of(SafeFuture.completedFuture(Optional.of(blobsBundle))),
                Optional.empty(),
                Optional.empty()));
  }

  private void prepareBlindedBlockAndBlobsProduction(
      final ExecutionPayloadHeader executionPayloadHeader,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final SszList<SszKZGCommitment> blobKzgCommitments) {
    final HeaderWithFallbackData headerWithFallbackData =
        HeaderWithFallbackData.create(executionPayloadHeader, Optional.of(blobKzgCommitments));
    when(executionLayer.initiateBlockAndBlobsProduction(
            executionPayloadContext, blockSlotState, true, BlockProductionPerformance.NOOP))
        .thenReturn(
            new ExecutionPayloadResult(
                executionPayloadContext,
                Optional.empty(),
                Optional.empty(),
                Optional.of(SafeFuture.completedFuture(headerWithFallbackData)),
                Optional.empty()));
  }

  private void prepareCachedPayloadResult(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BlobsBundle blobsBundle) {
    when(executionLayer.getCachedPayloadResult(slot))
        .thenReturn(
            Optional.of(
                new ExecutionPayloadResult(
                    executionPayloadContext,
                    Optional.of(SafeFuture.completedFuture(executionPayload)),
                    Optional.of(SafeFuture.completedFuture(Optional.of(blobsBundle))),
                    Optional.empty(),
                    Optional.empty())));
  }

  private void prepareCachedBuilderPayload(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final Optional<tech.pegasys.teku.spec.datastructures.builder.BlobsBundle> blobsBundle) {
    final BuilderPayload builderPayload =
        blobsBundle
            .map(
                bundle ->
                    (BuilderPayload)
                        SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions())
                            .getExecutionPayloadAndBlobsBundleSchema()
                            .create(executionPayload, bundle))
            .orElse(executionPayload);
    when(executionLayer.getCachedUnblindedPayload(slot)).thenReturn(Optional.of(builderPayload));
  }

  private static class CapturingBeaconBlockBodyBuilder implements BeaconBlockBodyBuilder {

    private final boolean supportsKzgCommitments;

    protected BLSSignature randaoReveal;
    protected Bytes32 graffiti;
    protected SszList<ProposerSlashing> proposerSlashings;
    protected SszList<AttesterSlashing> attesterSlashings;
    protected SszList<SignedVoluntaryExit> voluntaryExits;
    protected SszList<SignedBlsToExecutionChange> blsToExecutionChanges;
    protected SyncAggregate syncAggregate;
    protected ExecutionPayload executionPayload;
    protected ExecutionPayloadHeader executionPayloadHeader;
    protected SszList<SszKZGCommitment> blobKzgCommitments;

    public CapturingBeaconBlockBodyBuilder(final boolean supportsKzgCommitments) {
      this.supportsKzgCommitments = supportsKzgCommitments;
    }

    @Override
    public BeaconBlockBodyBuilder randaoReveal(final BLSSignature randaoReveal) {
      this.randaoReveal = randaoReveal;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder eth1Data(final Eth1Data eth1Data) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder graffiti(final Bytes32 graffiti) {
      this.graffiti = graffiti;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder attestations(final SszList<Attestation> attestations) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder proposerSlashings(
        final SszList<ProposerSlashing> proposerSlashings) {
      this.proposerSlashings = proposerSlashings;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder attesterSlashings(
        final SszList<AttesterSlashing> attesterSlashings) {
      this.attesterSlashings = attesterSlashings;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder deposits(final SszList<Deposit> deposits) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder voluntaryExits(
        final SszList<SignedVoluntaryExit> voluntaryExits) {
      this.voluntaryExits = voluntaryExits;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder syncAggregate(final SyncAggregate syncAggregate) {
      this.syncAggregate = syncAggregate;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder executionPayload(
        final SafeFuture<ExecutionPayload> executionPayload) {
      this.executionPayload = safeJoin(executionPayload);
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder executionPayloadHeader(
        final SafeFuture<ExecutionPayloadHeader> executionPayloadHeader) {
      this.executionPayloadHeader = safeJoin(executionPayloadHeader);
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder blsToExecutionChanges(
        final SszList<SignedBlsToExecutionChange> blsToExecutionChanges) {
      this.blsToExecutionChanges = blsToExecutionChanges;
      return this;
    }

    @Override
    public Boolean supportsSyncAggregate() {
      return true;
    }

    @Override
    public Boolean supportsExecutionPayload() {
      return true;
    }

    @Override
    public Boolean supportsBlsToExecutionChanges() {
      return true;
    }

    @Override
    public Boolean supportsKzgCommitments() {
      return supportsKzgCommitments;
    }

    @Override
    public BeaconBlockBodyBuilder blobKzgCommitments(
        final SafeFuture<SszList<SszKZGCommitment>> blobKzgCommitments) {
      this.blobKzgCommitments = safeJoin(blobKzgCommitments);
      return this;
    }

    @Override
    public SafeFuture<? extends BeaconBlockBody> build() {
      return null;
    }
  }

  private static class CapturingBeaconBlockUnblinder extends AbstractSignedBeaconBlockUnblinder {

    protected SafeFuture<ExecutionPayload> executionPayload;

    public CapturingBeaconBlockUnblinder(
        final SchemaDefinitions schemaDefinitions,
        final SignedBeaconBlock signedBlindedBeaconBlock) {
      super(schemaDefinitions, signedBlindedBeaconBlock);
    }

    @Override
    public void setExecutionPayloadSupplier(
        final Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
      this.executionPayload = executionPayloadSupplier.get();
    }

    @Override
    public SafeFuture<SignedBeaconBlock> unblind() {
      return null;
    }
  }
}
