/*
 * Copyright Consensys Software Inc., 2025
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.validator.coordinator.BlockOperationSelectorFactoryTest.CapturingBeaconBlockBodyBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.versions.fulu.SignedBlockContentsFulu;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.versions.fulu.BlobsBundleFulu;
import tech.pegasys.teku.spec.datastructures.execution.BlobsCellBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.SimpleOperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SignedContributionAndProofValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;

class BlockOperationSelectorFactoryTestFulu {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
      slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);

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
  private final Bytes32 parentRoot = dataStructureUtil.randomBytes32();

  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final ExecutionLayerBlockProductionManager executionLayer =
      mock(ExecutionLayerBlockProductionManager.class);

  private final GraffitiBuilder graffitiBuilder =
      new GraffitiBuilder(ClientGraffitiAppendFormat.DISABLED);

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
          graffitiBuilder,
          forkChoiceNotifier,
          executionLayer,
          metricsSystem,
          timeProvider);
  private ExecutionPayloadContext executionPayloadContext;

  @BeforeEach
  void setUp() {
    when(attestationPool.getAttestationsForBlock(any(), any()))
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
    this.executionPayloadContext = dataStructureUtil.randomPayloadExecutionContext(false);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
  }

  @Test
  void shouldIncludeKzgCommitmentsInBlindedBlock() {
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState();

    final ExecutionPayloadHeader randomExecutionPayloadHeader =
        dataStructureUtil.randomExecutionPayloadHeader();

    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    final SszList<SszKZGCommitment> blobKzgCommitments =
        dataStructureUtil.randomBlobKzgCommitments();

    final ExecutionPayloadContext executionPayloadContextWithValidatorRegistration =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(executionPayloadContextWithValidatorRegistration)));

    prepareBlindedBlockAndBlobsProduction(
        randomExecutionPayloadHeader,
        executionPayloadContextWithValidatorRegistration,
        blockSlotState,
        blobKzgCommitments,
        blockExecutionValue);

    final CapturingBeaconBlockBodyBuilder bodyBuilder =
        new CapturingBeaconBlockBodyBuilder(true) {
          @Override
          public Boolean supportsCellProofs() {
            return true;
          }
        };

    safeJoin(
        factory
            .createSelector(
                parentRoot,
                blockSlotState,
                dataStructureUtil.randomSignature(),
                Optional.empty(),
                Optional.empty(),
                BlockProductionPerformance.NOOP)
            .apply(bodyBuilder));

    assertThat(BeaconStateCache.getSlotCaches(blockSlotState).getBlockExecutionValue())
        .isEqualByComparingTo(blockExecutionValue);
    assertThat(bodyBuilder.blobKzgCommitments).hasSameElementsAs(blobKzgCommitments);
  }

  @Test
  void shouldGetBlobsCellBundleForLocallyProducedBlocks() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock();

    final BlobsCellBundle expectedBlobsCellBundle = dataStructureUtil.randomBlobsCellBundle();

    // the BlobsCellBundle is stored in the ExecutionPayloadResult
    prepareCachedPayloadResult(
        block.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        dataStructureUtil.randomPayloadExecutionContext(false),
        expectedBlobsCellBundle);

    final BlobsCellBundle blobsCellBundle =
        safeJoin(factory.createBlobsCellBundleSelector().apply(block));

    assertThat(blobsCellBundle).isEqualTo(expectedBlobsCellBundle);
  }

  @Test
  void shouldGetBlobsCellBundleForLocallyProducedBlocksViaFallback() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock();

    final BlobsCellBundle expectedBlobsCellBundle = dataStructureUtil.randomBlobsCellBundle();

    // the BlobsCellBundle is stored in the header with fallback
    prepareCachedPayloadHeaderWithFallbackResult(
        block.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        dataStructureUtil.randomPayloadExecutionContext(false),
        expectedBlobsCellBundle);

    final BlobsCellBundle blobsCellBundle =
        safeJoin(factory.createBlobsCellBundleSelector().apply(block));

    assertThat(blobsCellBundle).isEqualTo(expectedBlobsCellBundle);
  }

  @Test
  void shouldCreateDataColumnSidecarsForBlockContents() {
    final SignedBlockContentsFulu signedBlockContents =
        dataStructureUtil.randomSignedBlockContentsFulu();

    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.atSlot(signedBlockContents.getSlot()).miscHelpers());

    final KZG kzg = mock(KZG.class);
    when(kzg.computeCells(any()))
        .thenReturn(
            IntStream.range(0, 128).mapToObj(__ -> dataStructureUtil.randomKZGCell()).toList());
    final List<DataColumnSidecar> dataColumnSidecars =
        factory.createDataColumnSidecarsSelector(kzg).apply(signedBlockContents);

    final SszList<SszKZGProof> expectedProofs = signedBlockContents.getKzgProofs().orElseThrow();
    final SszList<SszKZGCommitment> expectedCommitments =
        signedBlockContents
            .getSignedBlock()
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow();

    assertThat(dataColumnSidecars).hasSize(CELLS_PER_EXT_BLOB);

    IntStream.range(0, dataColumnSidecars.size())
        .forEach(
            index -> {
              final DataColumnSidecar dataColumnSidecar = dataColumnSidecars.get(index);
              assertThat(dataColumnSidecar.getIndex()).isEqualTo(UInt64.valueOf(index));
              assertThat(dataColumnSidecar.getSignedBeaconBlockHeader())
                  .isEqualTo(signedBlockContents.getSignedBlock().asHeader());
              assertThat(dataColumnSidecar.getSszKZGProofs().asList())
                  .isEqualTo(
                      IntStream.range(0, expectedCommitments.size())
                          .mapToObj(
                              blobIndex ->
                                  expectedProofs.get(blobIndex * CELLS_PER_EXT_BLOB + index))
                          .toList());
              assertThat(dataColumnSidecar.getSszKZGCommitments()).isEqualTo(expectedCommitments);
              // verify the merkle proof
              assertThat(miscHelpersFulu.verifyDataColumnSidecarInclusionProof(dataColumnSidecar))
                  .isTrue();
            });
  }

  @Test
  void
      shouldFailCreatingDataColumnSidecarsIfBuilderBlobsCellBundleCommitmentsRootIsNotConsistent() {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);

    final BlobsBundleFulu blobsBundleFulu = dataStructureUtil.randomBuilderBlobsBundleFulu(3);

    prepareCachedBuilderPayload(
        signedBlindedBeaconBlock.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        blobsBundleFulu);

    assertThatThrownBy(
            () ->
                factory.createDataColumnSidecarsSelector(KZG.NOOP).apply(signedBlindedBeaconBlock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Commitments in the builder BlobsCellBundle don't match the commitments in the block");
  }

  @Test
  void shouldFailCreatingBlobSidecarsIfBuilderBlobsBundleProofsIsNotConsistent() {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);

    final BlobsBundleFulu blobsBundleFulu =
        spy(dataStructureUtil.randomBuilderBlobsBundleFulu(commitments));
    when(blobsBundleFulu.getBlobs()).thenReturn(dataStructureUtil.randomSszBlobs(2));

    prepareCachedBuilderPayload(
        signedBlindedBeaconBlock.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        blobsBundleFulu);

    assertThatThrownBy(
            () ->
                factory.createDataColumnSidecarsSelector(KZG.NOOP).apply(signedBlindedBeaconBlock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The number of blobs in the builder BlobsCellBundle doesn't match the number of commitments in the block");
  }

  @Test
  void shouldFailCreatingDataColumnSidecarsIfBuilderBlobsBundleBlobsIsNotConsistent() {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);

    final BlobsBundleFulu blobsBundle =
        spy(dataStructureUtil.randomBuilderBlobsBundleFulu(commitments));
    when(blobsBundle.getProofs()).thenReturn(dataStructureUtil.randomSszKZGProofs(3));

    prepareCachedBuilderPayload(
        signedBlindedBeaconBlock.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        blobsBundle);

    assertThatThrownBy(
            () ->
                factory.createDataColumnSidecarsSelector(KZG.NOOP).apply(signedBlindedBeaconBlock))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected 128 proofs but got 3");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldCreateDataColumnSidecarsForBlindedBlock(final boolean useLocalFallback) {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);
    final UInt64 slot = signedBlindedBeaconBlock.getSlot();

    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final BlobsBundleFulu blobsBundle = dataStructureUtil.randomBuilderBlobsBundleFulu(commitments);

    if (useLocalFallback) {
      final BlobsCellBundle localFallbackBlobsBundle =
          new BlobsCellBundle(
              blobsBundle.getCommitments().stream()
                  .map(SszKZGCommitment::getKZGCommitment)
                  .toList(),
              blobsBundle.getProofs().stream().map(SszKZGProof::getKZGProof).toList(),
              blobsBundle.getBlobs().stream().toList());
      prepareCachedFallbackData(slot, executionPayload, localFallbackBlobsBundle);
    } else {

      prepareCachedBuilderPayload(slot, executionPayload, blobsBundle);
    }

    final KZG kzg = mock(KZG.class);
    when(kzg.computeCells(any()))
        .thenReturn(
            IntStream.range(0, 128).mapToObj(__ -> dataStructureUtil.randomKZGCell()).toList());
    final List<DataColumnSidecar> dataColumnSidecars =
        factory.createDataColumnSidecarsSelector(kzg).apply(signedBlindedBeaconBlock);

    final SszList<SszKZGProof> expectedProofs = blobsBundle.getProofs();
    final SszList<SszKZGCommitment> expectedCommitments =
        signedBlindedBeaconBlock
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow();

    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.atSlot(slot).miscHelpers());

    assertThat(dataColumnSidecars).hasSize(CELLS_PER_EXT_BLOB);
    IntStream.range(0, dataColumnSidecars.size())
        .forEach(
            index -> {
              final DataColumnSidecar dataColumnSidecar = dataColumnSidecars.get(index);
              assertThat(dataColumnSidecar.getIndex()).isEqualTo(UInt64.valueOf(index));
              assertThat(dataColumnSidecar.getSignedBeaconBlockHeader())
                  .isEqualTo(signedBlindedBeaconBlock.asHeader());
              assertThat(dataColumnSidecar.getSszKZGProofs().asList())
                  .isEqualTo(
                      IntStream.range(0, expectedCommitments.size())
                          .mapToObj(
                              blobIndex ->
                                  expectedProofs.get(blobIndex * CELLS_PER_EXT_BLOB + index))
                          .toList());
              assertThat(dataColumnSidecar.getSszKZGCommitments()).isEqualTo(expectedCommitments);
              // verify the merkle proof
              assertThat(miscHelpersFulu.verifyDataColumnSidecarInclusionProof(dataColumnSidecar))
                  .isTrue();
            });
  }

  private void prepareBlindedBlockAndBlobsProduction(
      final ExecutionPayloadHeader executionPayloadHeader,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final SszList<SszKZGCommitment> blobKzgCommitments,
      final UInt256 executionPayloadValue) {
    final BuilderBidOrFallbackData builderBidOrFallbackData =
        BuilderBidOrFallbackData.create(
            dataStructureUtil.randomBuilderBid(
                builder -> {
                  builder.header(executionPayloadHeader);
                  builder.blobKzgCommitments(blobKzgCommitments);
                  builder.value(executionPayloadValue);
                }));
    when(executionLayer.initiateBlockProduction(
            executionPayloadContext,
            blockSlotState,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP))
        .thenReturn(
            ExecutionPayloadResult.createForBuilderFlow(
                executionPayloadContext, SafeFuture.completedFuture(builderBidOrFallbackData)));
  }

  private void prepareCachedPayloadResult(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BlobsCellBundle blobsCellBundle) {
    when(executionLayer.getCachedPayloadResult(slot))
        .thenReturn(
            Optional.of(
                ExecutionPayloadResult.createForLocalFlow(
                    executionPayloadContext,
                    SafeFuture.completedFuture(
                        new GetPayloadResponse(
                            executionPayload, UInt256.ZERO, blobsCellBundle, false)))));
  }

  private void prepareCachedPayloadHeaderWithFallbackResult(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BlobsCellBundle blobsCellBundle) {
    final BuilderBidOrFallbackData builderBidOrFallbackData =
        BuilderBidOrFallbackData.create(
            new FallbackData(
                new GetPayloadResponse(executionPayload, UInt256.ZERO, blobsCellBundle, false),
                FallbackReason.SHOULD_OVERRIDE_BUILDER_FLAG_IS_TRUE));
    when(executionLayer.getCachedPayloadResult(slot))
        .thenReturn(
            Optional.of(
                ExecutionPayloadResult.createForBuilderFlow(
                    executionPayloadContext,
                    SafeFuture.completedFuture(builderBidOrFallbackData))));
  }

  private void prepareCachedBuilderPayload(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final BlobsBundleFulu blobsCellBundle) {
    final BuilderPayload builderPayload =
        SchemaDefinitionsFulu.required(spec.atSlot(slot).getSchemaDefinitions())
            .getExecutionPayloadAndBlobsCellBundleSchema()
            .create(executionPayload, blobsCellBundle);
    when(executionLayer.getCachedUnblindedPayload(slot))
        .thenReturn(Optional.of(BuilderPayloadOrFallbackData.create(builderPayload)));
  }

  private void prepareCachedFallbackData(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final BlobsCellBundle blobsCellBundle) {
    when(executionLayer.getCachedUnblindedPayload(slot))
        .thenReturn(
            Optional.of(
                BuilderPayloadOrFallbackData.create(
                    new FallbackData(
                        new GetPayloadResponse(
                            executionPayload, UInt256.ZERO, blobsCellBundle, false),
                        FallbackReason.BUILDER_ERROR))));
  }
}
