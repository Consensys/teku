/*
 * Copyright ConsenSys Software Inc., 2023
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.builder.BlindedBlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

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

  protected ExecutionPayload executionPayload = null;
  protected Optional<BlobsBundle> blobsBundle = Optional.empty();

  protected ExecutionPayloadHeader executionPayloadHeader = null;
  protected Optional<BlindedBlobsBundle> blindedBlobsBundle = Optional.empty();

  protected ExecutionPayloadResult cachedExecutionPayloadResult = null;

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.depositSignatureVerifier = BLSSignatureVerifier.NO_OP;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.depositSignatureVerifier =
        AbstractBlockProcessor.DEFAULT_DEPOSIT_SIGNATURE_VERIFIER;
  }

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

  protected BlockContainer assertBlockCreated(
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
    when(attestationsPool.getAttestationsForBlock(any(), any(), any())).thenReturn(attestations);
    when(attesterSlashingPool.getItemsForBlock(any(), any(), any())).thenReturn(attesterSlashings);
    when(proposerSlashingPool.getItemsForBlock(any(), any(), any())).thenReturn(proposerSlashings);
    when(voluntaryExitPool.getItemsForBlock(any(), any(), any())).thenReturn(voluntaryExits);
    when(blsToExecutionChangePool.getItemsForBlock(any())).thenReturn(blsToExecutionChanges);
    when(eth1DataCache.getEth1Vote(any())).thenReturn(ETH1_DATA);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(dataStructureUtil.randomPayloadExecutionContext(false))));

    setupExecutionLayerBlockAndBlobsProduction();

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final Bytes32 bestBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final BeaconState blockSlotState =
        recentChainData
            .retrieveStateAtSlot(new SlotAndBlockRoot(UInt64.valueOf(blockSlot), bestBlockRoot))
            .join()
            .orElseThrow();

    when(syncCommitteeContributionPool.createSyncAggregateForBlock(newSlot, bestBlockRoot))
        .thenAnswer(invocation -> createEmptySyncAggregate(spec));
    executionPayloadBuilder.accept(blockSlotState);

    final BlockContainer blockContainer =
        safeJoin(
            blockFactory.createUnsignedBlock(
                blockSlotState, newSlot, randaoReveal, Optional.empty(), blinded));

    final BeaconBlock block = blockContainer.getBlock();

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

    if (milestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      assertThat(block.getBody().getOptionalBlobKzgCommitments())
          .hasValueSatisfying(
              blobKzgCommitments ->
                  assertThat(blobKzgCommitments)
                      .hasSameElementsAs(getCommitmentsFromBlobsBundle()));
    } else {
      assertThat(block.getBody().getOptionalBlobKzgCommitments()).isEmpty();
    }

    return blockContainer;
  }

  protected SyncAggregate createEmptySyncAggregate(final Spec spec) {
    return BeaconBlockBodySchemaAltair.required(
            spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema())
        .getSyncAggregateSchema()
        .createEmpty();
  }

  protected SignedBlockContainer assertBlockUnblinded(
      final SignedBlockContainer blindedBlockContainer, final Spec spec) {
    final BlockFactory blockFactory = createBlockFactory(spec);

    final BuilderPayload builderPayload = getBuilderPayload(spec);
    when(executionLayer.getUnblindedPayload(blindedBlockContainer))
        .thenReturn(SafeFuture.completedFuture(builderPayload));
    // simulate caching of the unblinded payload
    when(executionLayer.getCachedUnblindedPayload(blindedBlockContainer.getSlot()))
        .thenReturn(Optional.ofNullable(builderPayload));
    // used for unblinding the blob sidecars
    setupCachedBlobsBundle(blindedBlockContainer.getSlot());

    final SignedBlockContainer unblindedBlockContainer =
        blockFactory.unblindSignedBlockIfBlinded(blindedBlockContainer).join();

    final SignedBeaconBlock block = unblindedBlockContainer.getSignedBlock();

    if (!blindedBlockContainer.isBlinded()) {
      verifyNoInteractions(executionLayer);
    } else {
      verify(executionLayer).getUnblindedPayload(blindedBlockContainer);
    }

    assertThat(block).isNotNull();
    assertThat(block.hashTreeRoot())
        .isEqualTo(blindedBlockContainer.getSignedBlock().hashTreeRoot());
    assertThat(block.getMessage().getBody().isBlinded()).isFalse();
    assertThat(block.getMessage().getBody().getOptionalExecutionPayloadHeader())
        .isEqualTo(Optional.empty());

    return unblindedBlockContainer;
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
                    .parentHash(state.getLatestExecutionPayloadHeader().getBlockHash())
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

  protected BlindedBlobsBundle prepareBlindedBlobsBundle(final Spec spec, final int count) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BlindedBlobsBundle blindedBlobsBundle = dataStructureUtil.randomBlindedBlobsBundle(count);
    this.blindedBlobsBundle = Optional.of(blindedBlobsBundle);
    return blindedBlobsBundle;
  }

  private void setupExecutionLayerBlockAndBlobsProduction() {
    // pre Deneb
    when(executionLayer.initiateBlockProduction(any(), any(), eq(false)))
        .thenAnswer(
            args -> {
              final ExecutionPayloadResult executionPayloadResult =
                  new ExecutionPayloadResult(
                      args.getArgument(0),
                      Optional.of(SafeFuture.completedFuture(executionPayload)),
                      Optional.empty(),
                      Optional.empty());
              cachedExecutionPayloadResult = executionPayloadResult;
              return executionPayloadResult;
            });
    when(executionLayer.initiateBlockProduction(any(), any(), eq(true)))
        .thenAnswer(
            args -> {
              final ExecutionPayloadResult executionPayloadResult =
                  new ExecutionPayloadResult(
                      args.getArgument(0),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of(
                          SafeFuture.completedFuture(
                              HeaderWithFallbackData.create(executionPayloadHeader))));
              cachedExecutionPayloadResult = executionPayloadResult;
              return executionPayloadResult;
            });
    // post Deneb
    when(executionLayer.initiateBlockAndBlobsProduction(any(), any(), eq(false)))
        .thenAnswer(
            args -> {
              final ExecutionPayloadResult executionPayloadResult =
                  new ExecutionPayloadResult(
                      args.getArgument(0),
                      Optional.of(SafeFuture.completedFuture(executionPayload)),
                      Optional.of(SafeFuture.completedFuture(blobsBundle)),
                      Optional.empty());
              cachedExecutionPayloadResult = executionPayloadResult;
              return executionPayloadResult;
            });
    when(executionLayer.initiateBlockAndBlobsProduction(any(), any(), eq(true)))
        .thenAnswer(
            args -> {
              final ExecutionPayloadResult executionPayloadResult =
                  new ExecutionPayloadResult(
                      args.getArgument(0),
                      Optional.empty(),
                      Optional.empty(),
                      Optional.of(
                          SafeFuture.completedFuture(
                              HeaderWithFallbackData.create(
                                  executionPayloadHeader, blindedBlobsBundle))));
              cachedExecutionPayloadResult = executionPayloadResult;
              return executionPayloadResult;
            });
    // simulate caching of the payload result
    when(executionLayer.getCachedPayloadResult(any()))
        .thenAnswer(__ -> Optional.of(cachedExecutionPayloadResult));
  }

  private void setupCachedBlobsBundle(final UInt64 slot) {
    // only BlobsBundle is required
    final ExecutionPayloadResult executionPayloadResult =
        new ExecutionPayloadResult(
            null,
            Optional.empty(),
            Optional.of(SafeFuture.completedFuture(blobsBundle)),
            Optional.empty());
    when(executionLayer.getCachedPayloadResult(slot))
        .thenReturn(Optional.of(executionPayloadResult));
  }

  private List<SszKZGCommitment> getCommitmentsFromBlobsBundle() {
    return blobsBundle
        .map(
            blobsBundle ->
                blobsBundle.getCommitments().stream()
                    .map(SszKZGCommitment::new)
                    .collect(Collectors.toList()))
        .or(
            () ->
                blindedBlobsBundle.map(
                    blindedBlobsBundle -> blindedBlobsBundle.getCommitments().asList()))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Neither BlobsBundle or BlindedBlobsBundle were prepared"));
  }

  private BuilderPayload getBuilderPayload(final Spec spec) {
    // pre Deneb
    if (blobsBundle.isEmpty()) {
      return executionPayload;
    }
    // post Deneb
    final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions());
    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle builderBlobsBundle =
        schemaDefinitionsDeneb
            .getBlobsBundleSchema()
            .createFromExecutionBlobsBundle(blobsBundle.orElseThrow());
    return schemaDefinitionsDeneb
        .getExecutionPayloadAndBlobsBundleSchema()
        .create(executionPayload, builderBlobsBundle);
  }
}
