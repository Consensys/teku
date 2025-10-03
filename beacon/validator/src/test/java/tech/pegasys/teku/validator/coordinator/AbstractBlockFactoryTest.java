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

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.constants.EthConstants.GWEI_TO_WEI;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BlobsBundleDeneb;
import tech.pegasys.teku.spec.datastructures.builder.versions.fulu.BlobsBundleFulu;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.BlobsCellBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;

@SuppressWarnings("unchecked")
public abstract class AbstractBlockFactoryTest {

  private static final Eth1Data ETH1_DATA = new Eth1Data();

  protected final AggregatingAttestationPool attestationsPool =
      mock(AggregatingAttestationPool.class);
  protected final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  protected final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  protected final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);
  protected final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool =
      mock(OperationPool.class);
  protected final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  protected final ExecutionLayerBlockProductionManager executionLayer =
      mock(ExecutionLayerBlockProductionManager.class);
  protected final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  protected final DepositProvider depositProvider = mock(DepositProvider.class);
  protected final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  protected final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);

  // execution context
  protected ExecutionPayload executionPayload = null;
  protected Optional<BlobsBundle> blobsBundle = Optional.empty();
  protected Optional<BlobsCellBundle> blobsCellBundle = Optional.empty();

  // builder context
  protected ExecutionPayloadHeader executionPayloadHeader = null;
  protected Optional<SszList<SszKZGCommitment>> builderBlobKzgCommitments = Optional.empty();
  protected Optional<BuilderPayload> builderPayload = Optional.empty();

  protected ExecutionPayloadResult cachedExecutionPayloadResult = null;

  protected GraffitiBuilder graffitiBuilder =
      new GraffitiBuilder(ClientGraffitiAppendFormat.DISABLED);

  abstract BlockFactory createBlockFactory(Spec spec);

  protected SyncAggregate getSyncAggregate(final BeaconBlock block) {
    return BeaconBlockBodyAltair.required(block.getBody()).getSyncAggregate();
  }

  protected ExecutionPayload getExecutionPayload(final BeaconBlock block) {
    return BeaconBlockBodyBellatrix.required(block.getBody()).getExecutionPayload();
  }

  protected ExecutionPayloadHeader getExecutionPayloadHeader(final BeaconBlock block) {
    return BlindedBeaconBlockBodyBellatrix.required(block.getBody()).getExecutionPayloadHeader();
  }

  protected BlockContainerAndMetaData assertBlockCreated(
      final int blockSlot,
      final Spec spec,
      final boolean postMerge,
      final Consumer<BeaconState> executionPayloadBuilder,
      final boolean blinded) {
    final UInt64 newSlot = UInt64.valueOf(blockSlot);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BeaconBlockBodyLists blockBodyLists = BeaconBlockBodyLists.ofSpec(spec);
    final StorageSystem localChain = InMemoryStorageSystemBuilder.buildDefault(spec);
    final RecentChainData recentChainData = localChain.recentChainData();

    final SszList<Deposit> deposits = blockBodyLists.createDeposits();
    final SszList<Attestation> attestations = blockBodyLists.createAttestations();
    final SszList<AttesterSlashing> attesterSlashings = blockBodyLists.createAttesterSlashings();
    final SszList<ProposerSlashing> proposerSlashings = blockBodyLists.createProposerSlashings();
    final SszList<SignedVoluntaryExit> voluntaryExits = blockBodyLists.createVoluntaryExits();
    final SszList<SignedBlsToExecutionChange> blsToExecutionChanges =
        blockBodyLists.createBlsToExecutionChanges();

    final SpecMilestone milestone = spec.atSlot(newSlot).getMilestone();

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)) {
      if (postMerge) {
        localChain
            .chainUpdater()
            .initializeGenesisWithPayload(false, dataStructureUtil.randomExecutionPayloadHeader());
      } else {
        localChain.chainUpdater().initializeGenesis(false);
      }
    } else {
      checkArgument(
          !postMerge, "Cannot initialize genesis state post merge in non Bellatrix genesis");
      localChain.chainUpdater().initializeGenesis(false);
    }

    final BlockFactory blockFactory = createBlockFactory(spec);

    when(depositProvider.getDeposits(any(), any())).thenReturn(deposits);
    when(attestationsPool.getAttestationsForBlock(any(), any())).thenReturn(attestations);
    when(attesterSlashingPool.getItemsForBlock(any(), any(), any())).thenReturn(attesterSlashings);
    when(proposerSlashingPool.getItemsForBlock(any(), any(), any())).thenReturn(proposerSlashings);
    when(voluntaryExitPool.getItemsForBlock(any(), any(), any())).thenReturn(voluntaryExits);
    when(blsToExecutionChangePool.getItemsForBlock(any())).thenReturn(blsToExecutionChanges);
    when(eth1DataCache.getEth1Vote(any())).thenReturn(ETH1_DATA);
    if (blinded) {
      when(forkChoiceNotifier.getPayloadId(any(), any()))
          .thenReturn(
              SafeFuture.completedFuture(
                  Optional.of(dataStructureUtil.randomPayloadExecutionContext(false, true))));
    } else {
      when(forkChoiceNotifier.getPayloadId(any(), any()))
          .thenReturn(
              SafeFuture.completedFuture(
                  Optional.of(dataStructureUtil.randomPayloadExecutionContext(false))));
    }

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final Bytes32 bestBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final BeaconState blockSlotState =
        recentChainData
            .retrieveStateAtSlot(new SlotAndBlockRoot(UInt64.valueOf(blockSlot), bestBlockRoot))
            .join()
            .orElseThrow();

    when(syncCommitteeContributionPool.createSyncAggregateForBlock(newSlot, bestBlockRoot))
        .thenAnswer(invocation -> createEmptySyncAggregate(spec));

    UInt256 blockExecutionValue;
    final UInt64 blockProposerRewards;

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)) {
      blockExecutionValue = dataStructureUtil.randomUInt256();
      blockProposerRewards = dataStructureUtil.randomUInt64();
      // increase block proposer rewards to test the consensus block value
      final SlotCaches slotCaches = BeaconStateCache.getSlotCaches(blockSlotState);
      slotCaches.increaseBlockProposerRewards(blockProposerRewards);
    } else {
      blockExecutionValue = UInt256.ZERO;
      blockProposerRewards = UInt64.ZERO;
    }

    setupExecutionLayerBlockAndBlobsProduction(spec, blockExecutionValue);

    executionPayloadBuilder.accept(blockSlotState);

    final BlockContainerAndMetaData blockContainerAndMetaData =
        safeJoin(
            blockFactory.createUnsignedBlock(
                blockSlotState,
                newSlot,
                randaoReveal,
                Optional.empty(),
                Optional.empty(),
                BlockProductionPerformance.NOOP));

    final BeaconBlock block = blockContainerAndMetaData.blockContainer().getBlock();

    assertThat(block).isNotNull();
    assertThat(block.getSlot()).isEqualTo(newSlot);
    assertThat(block.getBody().getRandaoReveal()).isEqualTo(randaoReveal);
    assertThat(block.getBody().getEth1Data()).isEqualTo(ETH1_DATA);
    assertThat(block.getBody().getDeposits()).isEqualTo(deposits);
    assertThat(block.getBody().getAttestations()).isEqualTo(attestations);
    assertThat(block.getBody().getAttesterSlashings()).isEqualTo(attesterSlashings);
    assertThat(block.getBody().getProposerSlashings()).isEqualTo(proposerSlashings);
    assertThat(block.getBody().getVoluntaryExits()).isEqualTo(voluntaryExits);
    assertThat(block.getBody().getGraffiti()).isNotNull();

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      assertThat(block.getBody().getOptionalBlsToExecutionChanges())
          .isPresent()
          .hasValue(blsToExecutionChanges);
    } else {
      assertThat(block.getBody().getOptionalBlsToExecutionChanges()).isEmpty();
    }

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      // set the execution value to the value from the bid
      blockExecutionValue =
          GWEI_TO_WEI.multiply(
              block
                  .getBody()
                  .getOptionalSignedExecutionPayloadBid()
                  .orElseThrow()
                  .getMessage()
                  .getValue()
                  .longValue());
      // TODO-GLOAS: add mocked assertions https://github.com/Consensys/teku/issues/9959
      assertThat(block.getBody().getOptionalSignedExecutionPayloadBid()).isPresent();
      assertThat(block.getBody().getOptionalPayloadAttestations()).isPresent();
    } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      assertThat(block.getBody().getOptionalBlobKzgCommitments())
          .hasValueSatisfying(
              blobKzgCommitments ->
                  assertThat(blobKzgCommitments)
                      .hasSameElementsAs(
                          getCommitmentsFromBlobsCellBundleOrBuilderBlobKzgCommitments()));
    } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      assertThat(block.getBody().getOptionalBlobKzgCommitments())
          .hasValueSatisfying(
              blobKzgCommitments ->
                  assertThat(blobKzgCommitments)
                      .hasSameElementsAs(
                          getCommitmentsFromBlobsBundleOrBuilderBlobKzgCommitments()));
    } else {
      assertThat(block.getBody().getOptionalBlobKzgCommitments()).isEmpty();
    }

    assertThat(blockContainerAndMetaData.consensusBlockValue())
        .isEqualByComparingTo(GWEI_TO_WEI.multiply(blockProposerRewards.longValue()));
    assertThat(blockContainerAndMetaData.executionPayloadValue())
        .isEqualByComparingTo(blockExecutionValue);

    return blockContainerAndMetaData;
  }

  protected SyncAggregate createEmptySyncAggregate(final Spec spec) {
    return BeaconBlockBodySchemaAltair.required(
            spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema())
        .getSyncAggregateSchema()
        .createEmpty();
  }

  protected SignedBeaconBlock assertBlockUnblinded(
      final SignedBeaconBlock blindedBlock, final Spec spec) {
    final BlockFactory blockFactory = createBlockFactory(spec);

    // no need to prepare blobs bundle when only testing block unblinding
    when(executionLayer.getUnblindedPayload(blindedBlock, BlockPublishingPerformance.NOOP))
        .thenReturn(
            SafeFuture.completedFuture(BuilderPayloadOrFallbackData.create(executionPayload)));

    final SignedBeaconBlock unblindedBlock =
        blockFactory
            .unblindSignedBlockIfBlinded(blindedBlock, BlockPublishingPerformance.NOOP)
            .join()
            .orElseThrow();

    assertThat(unblindedBlock).isNotNull();
    assertThat(unblindedBlock.hashTreeRoot()).isEqualTo(blindedBlock.hashTreeRoot());
    assertThat(unblindedBlock.getMessage().getBody().isBlinded()).isFalse();
    assertThat(unblindedBlock.getMessage().getBody().getOptionalExecutionPayloadHeader())
        .isEqualTo(Optional.empty());

    if (blindedBlock.isBlinded()) {
      verify(executionLayer).getUnblindedPayload(blindedBlock, BlockPublishingPerformance.NOOP);
      assertThat(unblindedBlock.getMessage().getBody().getOptionalExecutionPayload())
          .hasValue(executionPayload);
    } else {
      verifyNoInteractions(executionLayer);
    }

    return unblindedBlock;
  }

  protected SignedBeaconBlock assertBlockBlinded(
      final SignedBeaconBlock beaconBlock, final Spec spec) {

    final SignedBeaconBlock block = blindSignedBeaconBlockIfUnblinded(spec, beaconBlock);

    assertThat(block).isNotNull();
    assertThat(block.hashTreeRoot()).isEqualTo(beaconBlock.hashTreeRoot());
    assertThat(block.getMessage().getBody().isBlinded()).isTrue();
    assertThat(block.getMessage().getBody().getOptionalExecutionPayload())
        .isEqualTo(Optional.empty());

    return block;
  }

  protected SignedBeaconBlock blindSignedBeaconBlockIfUnblinded(
      final Spec spec, final SignedBeaconBlock unblindedSignedBeaconBlock) {
    if (unblindedSignedBeaconBlock.isBlinded()) {
      return unblindedSignedBeaconBlock;
    }
    return spec.blindSignedBeaconBlock(unblindedSignedBeaconBlock);
  }

  protected BlockAndBlobSidecars createBlockAndBlobSidecars(
      final boolean blinded, final Spec spec) {
    final BlockFactory blockFactory = createBlockFactory(spec);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBlockContainer signedBlockContainer;

    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      if (blinded) {
        final SszList<SszKZGCommitment> commitments = getCommitmentsFromBuilderPayload();
        signedBlockContainer =
            dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);
      } else {
        BlobsBundle blobsBundle =
            this.blobsBundle.orElseThrow(
                () -> new IllegalStateException("BlobsBundle was not prepared"));
        signedBlockContainer = dataStructureUtil.randomSignedBlockContentsDeneb(blobsBundle);
      }
    } else {
      if (blinded) {
        signedBlockContainer = dataStructureUtil.randomSignedBlindedBeaconBlock();
      } else {
        signedBlockContainer = dataStructureUtil.randomSignedBeaconBlock();
      }
    }

    // simulate caching of the builder payload
    when(executionLayer.getCachedUnblindedPayload(signedBlockContainer.getSlot()))
        .thenReturn(builderPayload.map(BuilderPayloadOrFallbackData::create));

    final List<BlobSidecar> blobSidecars = blockFactory.createBlobSidecars(signedBlockContainer);

    return new BlockAndBlobSidecars(signedBlockContainer, blobSidecars);
  }

  protected BlockAndDataColumnSidecars createBlockAndDataColumnSidecars(
      final boolean blinded, final Spec spec) {
    final BlockFactory blockFactory = createBlockFactory(spec);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBlockContainer signedBlockContainer;

    if (blinded) {
      final SszList<SszKZGCommitment> commitments = getCommitmentsFromBuilderPayloadFulu();
      signedBlockContainer =
          dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);
    } else {
      BlobsCellBundle blobsCellBundle =
          this.blobsCellBundle.orElseThrow(
              () -> new IllegalStateException("BlobsCellBundle was not prepared"));
      signedBlockContainer = dataStructureUtil.randomSignedBlockContentsFulu(blobsCellBundle);
    }

    // simulate caching of the builder payload
    when(executionLayer.getCachedUnblindedPayload(signedBlockContainer.getSlot()))
        .thenReturn(builderPayload.map(BuilderPayloadOrFallbackData::create));

    final List<DataColumnSidecar> dataColumnSidecars =
        blockFactory.createDataColumnSidecars(signedBlockContainer);

    return new BlockAndDataColumnSidecars(signedBlockContainer, dataColumnSidecars);
  }

  protected void prepareDefaultPayload(final Spec spec) {
    executionPayload =
        SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
            .getExecutionPayloadSchema()
            .getDefault();

    executionPayloadHeader =
        SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
            .getExecutionPayloadHeaderSchema()
            .getHeaderOfDefaultPayload();
  }

  protected void prepareValidPayload(final Spec spec, final BeaconState genericState) {
    final BeaconStateBellatrix state = BeaconStateBellatrix.required(genericState);
    final MiscHelpers miscHelpers = spec.atSlot(state.getSlot()).miscHelpers();
    final BeaconStateAccessors beaconStateAccessors =
        spec.atSlot(state.getSlot()).beaconStateAccessors();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    executionPayload =
        dataStructureUtil.randomExecutionPayload(
            genericState.getSlot(),
            builder ->
                builder
                    .parentHash(state.getLatestExecutionPayloadHeaderRequired().getBlockHash())
                    .prevRandao(
                        beaconStateAccessors.getRandaoMix(
                            state, beaconStateAccessors.getCurrentEpoch(state)))
                    .timestamp(
                        miscHelpers.computeTimeAtSlot(state.getGenesisTime(), state.getSlot()))
                    .withdrawals(Collections::emptyList));
    executionPayloadHeader =
        SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
            .getExecutionPayloadHeaderSchema()
            .createFromExecutionPayload(executionPayload);
  }

  protected BlobsBundle prepareBlobsBundle(final Spec spec, final int count) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle(count);
    this.blobsBundle = Optional.of(blobsBundle);
    return blobsBundle;
  }

  protected BlobsCellBundle prepareBlobsCellBundle(final Spec spec, final int count) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BlobsCellBundle blobsCellBundle = dataStructureUtil.randomBlobsCellBundle(count);
    this.blobsCellBundle = Optional.of(blobsCellBundle);
    return blobsCellBundle;
  }

  protected SszList<SszKZGCommitment> prepareBuilderBlobKzgCommitments(
      final Spec spec, final int count) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SszList<SszKZGCommitment> blobKzgCommitments =
        dataStructureUtil.randomBlobKzgCommitments(count);
    this.builderBlobKzgCommitments = Optional.of(blobKzgCommitments);
    return blobKzgCommitments;
  }

  protected BuilderPayload prepareBuilderPayload(final Spec spec, final int blobsCount) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final ExecutionPayload builderExecutionPayload =
        Optional.ofNullable(executionPayload).orElseGet(dataStructureUtil::randomExecutionPayload);
    final BuilderPayload builderPayload;
    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      final SchemaDefinitionsFulu schemaDefinitionsFulu =
          SchemaDefinitionsFulu.required(spec.getGenesisSchemaDefinitions());
      builderPayload =
          schemaDefinitionsFulu
              .getExecutionPayloadAndBlobsCellBundleSchema()
              .create(
                  builderExecutionPayload,
                  dataStructureUtil.randomBuilderBlobsBundleFulu(blobsCount));
    } else if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
          SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions());
      builderPayload =
          schemaDefinitionsDeneb
              .getExecutionPayloadAndBlobsBundleSchema()
              .create(
                  builderExecutionPayload, dataStructureUtil.randomBuilderBlobsBundle(blobsCount));
    } else {
      builderPayload = builderExecutionPayload;
    }
    this.builderPayload = Optional.of(builderPayload);

    return builderPayload;
  }

  private void setupExecutionLayerBlockAndBlobsProduction(final Spec spec, final UInt256 value) {
    // non-blinded
    when(executionLayer.initiateBlockProduction(any(), any(), eq(false), any(), any()))
        .thenAnswer(
            args -> {
              final GetPayloadResponse getPayloadResponse;

              if (blobsCellBundle.isPresent()) {
                final ExecutionRequestsSchema executionRequestsSchema =
                    SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
                        .getExecutionRequestsSchema();
                final ExecutionRequests executionRequests =
                    executionRequestsSchema.create(List.of(), List.of(), List.of());
                getPayloadResponse =
                    new GetPayloadResponse(
                        executionPayload, value, blobsCellBundle.get(), false, executionRequests);
              } else if (blobsBundle.isPresent()) {
                if (spec.isMilestoneSupported(SpecMilestone.ELECTRA)) {
                  final ExecutionRequestsSchema executionRequestsSchema =
                      SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
                          .getExecutionRequestsSchema();
                  final ExecutionRequests executionRequests =
                      executionRequestsSchema.create(List.of(), List.of(), List.of());
                  getPayloadResponse =
                      new GetPayloadResponse(
                          executionPayload, value, blobsBundle.get(), false, executionRequests);
                } else {
                  getPayloadResponse =
                      new GetPayloadResponse(executionPayload, value, blobsBundle.get(), false);
                }
              } else {
                getPayloadResponse = new GetPayloadResponse(executionPayload, value);
              }

              final ExecutionPayloadResult executionPayloadResult =
                  ExecutionPayloadResult.createForLocalFlow(
                      args.getArgument(0), SafeFuture.completedFuture(getPayloadResponse));
              cachedExecutionPayloadResult = executionPayloadResult;
              return executionPayloadResult;
            });
    // blinded
    when(executionLayer.initiateBlockProduction(any(), any(), eq(true), any(), any()))
        .thenAnswer(
            args -> {
              final BuilderBid builderBid =
                  SchemaDefinitionsBellatrix.required(spec.getGenesisSchemaDefinitions())
                      .getBuilderBidSchema()
                      .createBuilderBid(
                          builder -> {
                            builder.header(executionPayloadHeader);
                            builderBlobKzgCommitments.ifPresent(builder::blobKzgCommitments);
                            builder.value(value);
                            builder.publicKey(BLSPublicKey.empty());
                            if (spec.isMilestoneSupported(SpecMilestone.ELECTRA)) {
                              final ExecutionRequestsSchema executionRequestsSchema =
                                  SchemaDefinitionsElectra.required(
                                          spec.getGenesisSchemaDefinitions())
                                      .getExecutionRequestsSchema();
                              builder.executionRequests(
                                  executionRequestsSchema.create(List.of(), List.of(), List.of()));
                            }
                          });
              final ExecutionPayloadResult executionPayloadResult =
                  ExecutionPayloadResult.createForBuilderFlow(
                      args.getArgument(0),
                      SafeFuture.completedFuture(BuilderBidOrFallbackData.create(builderBid)));
              cachedExecutionPayloadResult = executionPayloadResult;
              return executionPayloadResult;
            });
    // simulate caching of the payload result
    when(executionLayer.getCachedPayloadResult(any()))
        .thenAnswer(__ -> Optional.of(cachedExecutionPayloadResult));
  }

  private List<SszKZGCommitment> getCommitmentsFromBlobsBundleOrBuilderBlobKzgCommitments() {
    return blobsBundle
        .map(
            blobsBundle ->
                blobsBundle.getCommitments().stream()
                    .map(SszKZGCommitment::new)
                    .collect(Collectors.toList()))
        .or(() -> builderBlobKzgCommitments.map(SszList::asList))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Neither BlobsBundle or builder BlobKzgCommitments were prepared"));
  }

  private List<SszKZGCommitment> getCommitmentsFromBlobsCellBundleOrBuilderBlobKzgCommitments() {
    return blobsCellBundle
        .map(
            blobsCellBundle ->
                blobsCellBundle.getCommitments().stream()
                    .map(SszKZGCommitment::new)
                    .collect(Collectors.toList()))
        .or(() -> builderBlobKzgCommitments.map(SszList::asList))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Neither BlobsCellBundle or builder BlobKzgCommitments were prepared"));
  }

  private SszList<SszKZGCommitment> getCommitmentsFromBuilderPayload() {
    return builderPayload
        .flatMap(BuilderPayload::getOptionalBlobsBundle)
        .map(BlobsBundleDeneb::getCommitments)
        .orElseThrow(() -> new IllegalStateException("BuilderPayload was not prepared"));
  }

  private SszList<SszKZGCommitment> getCommitmentsFromBuilderPayloadFulu() {
    return builderPayload
        .flatMap(BuilderPayload::getOptionalBlobsCellBundle)
        .map(BlobsBundleFulu::getCommitments)
        .orElseThrow(() -> new IllegalStateException("BuilderPayload was not prepared"));
  }

  protected record BlockAndBlobSidecars(
      SignedBlockContainer block, List<BlobSidecar> blobSidecars) {}

  protected record BlockAndDataColumnSidecars(
      SignedBlockContainer block, List<DataColumnSidecar> dataColumnSidecars) {}
}
