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

package tech.pegasys.teku.validator.coordinator;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateAssert.assertThatSyncAggregate;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@SuppressWarnings("unchecked")
class BlockFactoryTest {
  private static final Eth1Data ETH1_DATA = new Eth1Data();

  final AggregatingAttestationPool attestationsPool = mock(AggregatingAttestationPool.class);
  final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);
  final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool =
      mock(OperationPool.class);
  final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  final DepositProvider depositProvider = mock(DepositProvider.class);
  final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  ExecutionPayload executionPayload = null;
  ExecutionPayloadHeader executionPayloadHeader = null;

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @Test
  public void shouldCreateBlockAfterNormalSlot() {
    assertBlockCreated(1, TestSpecFactory.createMinimalPhase0(), false, false);
  }

  @Test
  public void shouldCreateBlockAfterSkippedSlot() {
    assertBlockCreated(2, TestSpecFactory.createMinimalPhase0(), false, false);
  }

  @Test
  public void shouldCreateBlockAfterMultipleSkippedSlot() {
    assertBlockCreated(5, TestSpecFactory.createMinimalPhase0(), false, false);
  }

  @Test
  void shouldIncludeSyncAggregateWhenAltairIsActive() {
    final BeaconBlock block =
        assertBlockCreated(1, TestSpecFactory.createMinimalAltair(), false, false);
    final SyncAggregate result = getSyncAggregate(block);
    assertThatSyncAggregate(result).isNotNull();
    verify(syncCommitteeContributionPool)
        .createSyncAggregateForBlock(UInt64.ONE, block.getParentRoot());
  }

  @Test
  void shouldIncludeExecutionPayloadWhenBellatrixIsActive() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();

    prepareDefaultPayload(spec);

    final BeaconBlock block = assertBlockCreated(1, spec, false, false);
    final ExecutionPayload result = getExecutionPayload(block);
    assertThat(result).isEqualTo(executionPayload);
  }

  @Test
  void shouldCreateCapellaBlock() {
    final Spec spec = TestSpecFactory.createMinimalCapella();
    prepareDefaultPayload(spec);

    final BeaconBlock block = assertBlockCreated(1, spec, false, false);
    final SszList<SignedBlsToExecutionChange> blsToExecutionChanges =
        BeaconBlockBodyCapella.required(block.getBody()).getBlsToExecutionChanges();
    assertThat(blsToExecutionChanges).isNotNull();
  }

  @Test
  void shouldIncludeExecutionPayloadHeaderWhenBellatrixIsActiveAndBlindedBlockRequested() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();

    prepareDefaultPayload(spec);

    final BeaconBlock block = assertBlockCreated(1, spec, false, true);
    final ExecutionPayloadHeader result = getExecutionPayloadHeader(block);
    assertThat(result).isEqualTo(executionPayloadHeader);
  }

  @Test
  void shouldThrowPostMergeWithWrongPayload() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();

    prepareDefaultPayload(spec);

    assertThatThrownBy(() -> assertBlockCreated(1, spec, true, false))
        .hasCauseInstanceOf(StateTransitionException.class);
  }

  @Test
  void unblindSignedBeaconBlock_shouldThrowWhenUnblindingBlockWithInconsistentExecutionPayload() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock signedBlock = dataStructureUtil.randomSignedBlindedBeaconBlock(1);
    executionPayload = dataStructureUtil.randomExecutionPayload();

    assertThatThrownBy(() -> assertBlockUnblinded(signedBlock, spec))
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void unblindSignedBeaconBlock_shouldPassthroughUnblindedBlocks() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalUnblindedSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    final SignedBeaconBlock unblindedSignedBlock =
        assertBlockUnblinded(originalUnblindedSignedBlock, spec);

    assertThat(unblindedSignedBlock).isEqualTo(originalUnblindedSignedBlock);
  }

  @Test
  void unblindSignedBeaconBlock_shouldPassthroughInNonBellatrixBlocks() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalAltairSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    final SignedBeaconBlock unblindedSignedBlock =
        assertBlockUnblinded(originalAltairSignedBlock, spec);

    assertThat(unblindedSignedBlock).isEqualTo(originalAltairSignedBlock);
  }

  @Test
  void blindSignedBeaconBlock_shouldThrowInNonBellatrixBlocks() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalUnblindedSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    assertThatThrownBy(() -> assertBlockBlinded(originalUnblindedSignedBlock, spec))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void blindSignedBeaconBlock_shouldBlindBlockWhenBellatrixIsActive() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalUnblindedSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    assertBlockBlinded(originalUnblindedSignedBlock, spec);
  }

  @Test
  void unblindSignedBeaconBlock_shouldUnblindingBlockWhenBellatrixIsActive() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalUnblindedSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    // now we have a blinded block
    final SignedBeaconBlock originalBlindedSignedBlock =
        assertBlockBlinded(originalUnblindedSignedBlock, spec);

    // let the unblinder return a consistent execution payload
    executionPayload =
        originalUnblindedSignedBlock
            .getMessage()
            .getBody()
            .getOptionalExecutionPayload()
            .orElseThrow();

    assertBlockUnblinded(originalBlindedSignedBlock, spec);
  }

  private SyncAggregate getSyncAggregate(final BeaconBlock block) {
    return BeaconBlockBodyAltair.required(block.getBody()).getSyncAggregate();
  }

  private ExecutionPayload getExecutionPayload(final BeaconBlock block) {
    return BeaconBlockBodyBellatrix.required(block.getBody()).getExecutionPayload();
  }

  private ExecutionPayloadHeader getExecutionPayloadHeader(final BeaconBlock block) {
    return BlindedBeaconBlockBodyBellatrix.required(block.getBody()).getExecutionPayloadHeader();
  }

  private BeaconBlock assertBlockCreated(
      final int blockSlot, final Spec spec, final boolean postMerge, final boolean blinded) {
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

    if (spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)) {
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

    final Bytes32 graffiti = dataStructureUtil.randomBytes32();
    final BlockFactory blockFactory =
        new BlockFactory(
            spec,
            new BlockOperationSelectorFactory(
                spec,
                attestationsPool,
                attesterSlashingPool,
                proposerSlashingPool,
                voluntaryExitPool,
                blsToExecutionChangePool,
                syncCommitteeContributionPool,
                depositProvider,
                eth1DataCache,
                graffiti,
                forkChoiceNotifier,
                executionLayer));

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
    when(executionLayer.initiateBlockProduction(any(), any(), eq(false)))
        .then(
            args ->
                new ExecutionPayloadResult(
                    args.getArgument(0),
                    Optional.of(SafeFuture.completedFuture(executionPayload)),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()));
    when(executionLayer.initiateBlockProduction(any(), any(), eq(true)))
        .then(
            args ->
                new ExecutionPayloadResult(
                    args.getArgument(0),
                    Optional.empty(),
                    Optional.of(SafeFuture.completedFuture(executionPayloadHeader)),
                    Optional.empty(),
                    Optional.empty()));

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final Bytes32 bestBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final BeaconState blockSlotState =
        recentChainData
            .retrieveStateAtSlot(new SlotAndBlockRoot(UInt64.valueOf(blockSlot), bestBlockRoot))
            .join()
            .orElseThrow();

    when(syncCommitteeContributionPool.createSyncAggregateForBlock(newSlot, bestBlockRoot))
        .thenAnswer(invocation -> createEmptySyncAggregate(spec));

    final BeaconBlock block =
        safeJoin(
            blockFactory.createUnsignedBlock(
                blockSlotState, newSlot, randaoReveal, Optional.empty(), blinded));

    assertThat(block).isNotNull();
    assertThat(block.getSlot()).isEqualTo(newSlot);
    assertThat(block.getBody().getRandaoReveal()).isEqualTo(randaoReveal);
    assertThat(block.getBody().getEth1Data()).isEqualTo(ETH1_DATA);
    assertThat(block.getBody().getDeposits()).isEqualTo(deposits);
    assertThat(block.getBody().getAttestations()).isEqualTo(attestations);
    assertThat(block.getBody().getAttesterSlashings()).isEqualTo(attesterSlashings);
    assertThat(block.getBody().getProposerSlashings()).isEqualTo(proposerSlashings);
    assertThat(block.getBody().getVoluntaryExits()).isEqualTo(voluntaryExits);
    assertThat(block.getBody().getGraffiti()).isEqualTo(graffiti);

    if (spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      assertThat(block.getBody().getOptionalBlsToExecutionChanges())
          .isPresent()
          .hasValue(blsToExecutionChanges);
    } else {
      assertThat(block.getBody().getOptionalBlsToExecutionChanges()).isEmpty();
    }

    return block;
  }

  private SyncAggregate createEmptySyncAggregate(final Spec spec) {
    return BeaconBlockBodySchemaAltair.required(
            spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema())
        .getSyncAggregateSchema()
        .createEmpty();
  }

  private SignedBeaconBlock assertBlockUnblinded(
      final SignedBeaconBlock beaconBlock, final Spec spec) {
    final BlockFactory blockFactory = createBlockFactory(spec);

    when(executionLayer.builderGetPayload(beaconBlock))
        .thenReturn(SafeFuture.completedFuture(executionPayload));

    final SignedBeaconBlock block =
        blockFactory.unblindSignedBeaconBlockIfBlinded(beaconBlock).join();

    if (!beaconBlock.getMessage().getBody().isBlinded()) {
      verifyNoInteractions(executionLayer);
    } else {
      verify(executionLayer).builderGetPayload(beaconBlock);
    }

    assertThat(block).isNotNull();
    assertThat(block.hashTreeRoot()).isEqualTo(beaconBlock.hashTreeRoot());
    assertThat(block.getMessage().getBody().isBlinded()).isFalse();
    assertThat(block.getMessage().getBody().getOptionalExecutionPayloadHeader())
        .isEqualTo(Optional.empty());

    return block;
  }

  private SignedBeaconBlock assertBlockBlinded(
      final SignedBeaconBlock beaconBlock, final Spec spec) {

    final BlockFactory blockFactory = createBlockFactory(spec);

    final SignedBeaconBlock block = blockFactory.blindSignedBeaconBlockIfUnblinded(beaconBlock);

    assertThat(block).isNotNull();
    assertThat(block.hashTreeRoot()).isEqualTo(beaconBlock.hashTreeRoot());
    assertThat(block.getMessage().getBody().isBlinded()).isTrue();
    assertThat(block.getMessage().getBody().getOptionalExecutionPayload())
        .isEqualTo(Optional.empty());

    return block;
  }

  private BlockFactory createBlockFactory(final Spec spec) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final Bytes32 graffiti = dataStructureUtil.randomBytes32();
    return new BlockFactory(
        spec,
        new BlockOperationSelectorFactory(
            spec,
            attestationsPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            blsToExecutionChangePool,
            syncCommitteeContributionPool,
            depositProvider,
            eth1DataCache,
            graffiti,
            forkChoiceNotifier,
            executionLayer));
  }

  private void prepareDefaultPayload(final Spec spec) {
    executionPayload =
        SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
            .getExecutionPayloadSchema()
            .getDefault();

    executionPayloadHeader =
        SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
            .getExecutionPayloadHeaderSchema()
            .getHeaderOfDefaultPayload();
  }
}
