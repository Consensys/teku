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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyBuilderGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(
    milestone = {
      SpecMilestone.ALTAIR,
      SpecMilestone.BELLATRIX,
      SpecMilestone.DENEB,
      SpecMilestone.ELECTRA,
      SpecMilestone.FULU,
      SpecMilestone.GLOAS
    },
    signatureVerifierNoop = true)
public class BlockGossipValidatorTest {
  private Spec spec;
  private RecentChainData recentChainData;
  private StorageSystem storageSystem;
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher =
      mock(ReceivedBlockEventsChannel.class);

  private BlockGossipValidator blockGossipValidator;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis(false);
    recentChainData = storageSystem.recentChainData();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);
  }

  @TestTemplate
  void shouldReturnValidForValidBlock() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    assertResultIsAccept(block, blockGossipValidator.validate(block, true));
  }

  @TestTemplate
  void shouldIgnoreAlreadyImportedBlock() {
    final SignedBeaconBlock block = storageSystem.chainUpdater().advanceChain().getBlock();

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreAlreadySeenBlocks() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    assertResultIsAccept(block, blockGossipValidator.validate(block, true));

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldReturnSavedForFutureForBlockFromFuture() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBeaconBlock block =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldReturnSavedForFutureForBlockWithParentUnavailable() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    final UInt64 proposerIndex = signedBlock.getMessage().getProposerIndex();
    final BeaconBlock block =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            signedBlock.getSlot(),
            proposerIndex,
            Bytes32.ZERO,
            signedBlock.getMessage().getStateRoot(),
            signedBlock.getMessage().getBody());

    BLSSignature blockSignature =
        storageSystem
            .chainBuilder()
            .getSigner(proposerIndex.intValue())
            .signBlock(
                block,
                storageSystem.chainBuilder().getLatestBlockAndState().getState().getForkInfo())
            .join();
    final SignedBeaconBlock blockWithNoParent =
        SignedBeaconBlock.create(spec, block, blockSignature);

    assertThat(blockGossipValidator.validate(blockWithNoParent, true))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldReturnInvalidForBlockOlderThanFinalizedSlot() {
    UInt64 finalizedEpoch = UInt64.valueOf(10);
    UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    storageSystem.chainUpdater().advanceChain(finalizedSlot);
    storageSystem.chainUpdater().finalizeEpoch(finalizedEpoch);

    StorageSystem storageSystem2 = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem2.chainUpdater().initializeGenesis(false);
    final SignedBeaconBlock block =
        storageSystem2.chainBuilder().generateBlockAtSlot(finalizedSlot.minus(ONE)).getBlock();

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldReturnInvalidForBlockWithWrongProposerIndex() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    final UInt64 invalidProposerIndex = signedBlock.getMessage().getProposerIndex().plus(ONE);

    final BeaconBlock block =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            signedBlock.getSlot(),
            invalidProposerIndex,
            signedBlock.getParentRoot(),
            signedBlock.getMessage().getStateRoot(),
            signedBlock.getMessage().getBody());

    final BLSSignature blockSignature =
        storageSystem
            .chainBuilder()
            .getSigner(invalidProposerIndex.intValue())
            .signBlock(
                block,
                storageSystem.chainBuilder().getLatestBlockAndState().getState().getForkInfo())
            .join();
    final SignedBeaconBlock invalidProposerSignedBlock =
        SignedBeaconBlock.create(spec, block, blockSignature);

    assertThat(blockGossipValidator.validate(invalidProposerSignedBlock, true))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.equals(
                    InternalValidationResult.reject(
                        "Block proposed by incorrect proposer (%s)", invalidProposerIndex)));
  }

  @TestTemplate
  void shouldReturnInvalidForBlockWithWrongSignature() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock block =
        SignedBeaconBlock.create(
            spec,
            storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock().getMessage(),
            BLSTestUtil.randomSignature(0));

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.equals(
                    InternalValidationResult.reject("Block signature is invalid")));
  }

  @TestTemplate
  void shouldReturnInvalidForBlockThatDoesNotDescendFromFinalizedCheckpoint() {
    List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(4);

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    final RecentChainData localRecentChainData = storageSystem.recentChainData();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec, validatorKeys);
    final ChainUpdater chainUpdater = new ChainUpdater(localRecentChainData, chainBuilder, spec);

    final BlockGossipValidator blockValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(
                spec, localRecentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);
    chainUpdater.initializeGenesis();

    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    final ChainBuilder chainBuilderFork = chainBuilder.fork();
    final ChainUpdater chainUpdaterFork =
        new ChainUpdater(storageSystem.recentChainData(), chainBuilderFork, spec);

    final UInt64 startSlotOfFinalizedEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(4));

    chainUpdaterFork.advanceChain(20);

    chainUpdater.finalizeEpoch(4);

    SignedBlockAndState blockAndState =
        chainBuilderFork.generateBlockAtSlot(startSlotOfFinalizedEpoch.increment());
    chainUpdater.saveBlockTime(blockAndState);
    final SafeFuture<InternalValidationResult> result =
        blockValidator.validate(blockAndState.getBlock(), true);
    assertThat(result)
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.equals(
                    InternalValidationResult.reject(
                        "Block does not descend from finalized checkpoint")));
  }

  @TestTemplate
  void shouldReturnAcceptOnCorrectExecutionPayloadTimestamp(final SpecContext specContext) {
    specContext.assumeBellatrixActive();

    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(
            false, specContext.getDataStructureUtil().randomExecutionPayloadHeader());
    recentChainData = storageSystem.recentChainData();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    SignedBeaconBlock block = storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    assertResultIsAccept(block, blockGossipValidator.validate(block, true));
  }

  @TestTemplate
  void shouldReturnInvalidOnWrongExecutionPayloadTimestamp(final SpecContext specContext) {
    specContext.assumeMilestonesActive(SpecMilestone.BELLATRIX, SpecMilestone.FULU);

    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(
            false, specContext.getDataStructureUtil().randomExecutionPayloadHeader());
    recentChainData = storageSystem.recentChainData();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBlockAndState signedBlockAndState =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(
                nextSlot,
                BlockOptions.create()
                    .setSkipStateTransition(true)
                    .setExecutionPayload(
                        specContext.getDataStructureUtil().randomExecutionPayload()));

    assertThat(blockGossipValidator.validate(signedBlockAndState.getBlock(), true))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.equals(
                    InternalValidationResult.reject(
                        "Execution Payload timestamp is not consistent with block slot time")));
  }

  @TestTemplate
  void shouldNotTrackBlocksIfMarkAsReceivedIsFalse() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    assertResultIsAccept(block, blockGossipValidator.validate(block, false));
    assertThat(blockGossipValidator.performBlockEquivocationCheck(true, block))
        .isEqualByComparingTo(EquivocationCheckResult.FIRST_BLOCK_FOR_SLOT_PROPOSER);
  }

  @TestTemplate
  void shouldRejectWhenKzgCommitmentsExceedLimit(final SpecContext specContext) {
    // We check kzg commitments in Deneb and Fulu only
    specContext.assumeMilestonesActive(SpecMilestone.DENEB, SpecMilestone.FULU);
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(
            false, specContext.getDataStructureUtil().randomExecutionPayloadHeader());
    recentChainData = storageSystem.recentChainData();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final Integer maxBlobsPerBlock =
        specContext.getSpec().getMaxBlobsPerBlockAtSlot(nextSlot).orElseThrow();

    final SignedBlockAndState signedBlockAndState =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(
                nextSlot,
                BlockOptions.create()
                    .setSkipStateTransition(true)
                    .setExecutionPayload(
                        specContext.getDataStructureUtil().randomExecutionPayload())
                    .setKzgCommitments(
                        specContext
                            .getDataStructureUtil()
                            .randomBlobKzgCommitments(maxBlobsPerBlock + 1)));

    assertThat(blockGossipValidator.validate(signedBlockAndState.getBlock(), true))
        .isCompletedWithValueMatching(
            result ->
                result.equals(
                    InternalValidationResult.reject(
                        "Block has %d kzg commitments, max allowed %d",
                        maxBlobsPerBlock + 1, maxBlobsPerBlock)));
  }

  @TestTemplate
  void shouldRejectBlockWhenExecutionPayloadBidParentRootIsInvalid(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final Bytes32 badParentBlockRoot = Bytes32.random();

    final SignedBeaconBlock invalidBlock =
        createBlockWithModifiedExecutionPayloadBid(
            signedBlockAndState,
            originalExecutionPayloadBid ->
                originalExecutionPayloadBid
                    .getSchema()
                    .create(
                        originalExecutionPayloadBid.getParentBlockHash(),
                        badParentBlockRoot,
                        originalExecutionPayloadBid.getBlockHash(),
                        originalExecutionPayloadBid.getPrevRandao(),
                        originalExecutionPayloadBid.getFeeRecipient(),
                        originalExecutionPayloadBid.getGasLimit(),
                        originalExecutionPayloadBid.getBuilderIndex(),
                        originalExecutionPayloadBid.getSlot(),
                        originalExecutionPayloadBid.getValue(),
                        originalExecutionPayloadBid.getExecutionPayment(),
                        originalExecutionPayloadBid.getBlobKzgCommitmentsRoot()));

    assertThat(blockGossipValidator.validate(invalidBlock, true))
        .isCompletedWithValueMatching(
            result ->
                result.equals(
                    InternalValidationResult.reject(
                        "Execution payload bid has invalid parent block root %s",
                        badParentBlockRoot)));
  }

  @TestTemplate
  void shouldRejectBlockWithIncorrectExecutionPayloadBidParentRoot(final SpecContext specContext) {
    specContext.assumeGloasActive();
    // Mocking the gossip helper to cover the case when the bid parent block is valid but not the
    // same as the parent block root
    final GossipValidationHelper gossipValidationHelperMock = mock(GossipValidationHelper.class);
    final BlockGossipValidator blockGossipValidatorMocked =
        new BlockGossipValidator(
            spec, gossipValidationHelperMock, receivedBlockEventsChannelPublisher);
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final BeaconState state = signedBlockAndState.getState();
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final Bytes32 expectedParentRoot = block.getParentRoot();
    final Bytes32 badBidParentBlockRoot = Bytes32.random();

    when(gossipValidationHelperMock.isSlotFinalized(block.getSlot())).thenReturn(false);
    when(gossipValidationHelperMock.isSlotFromFuture(block.getSlot())).thenReturn(false);
    when(gossipValidationHelperMock.isBlockAvailable(block.getRoot())).thenReturn(false);
    when(gossipValidationHelperMock.isBlockAvailable(block.getParentRoot())).thenReturn(true);
    when(gossipValidationHelperMock.currentFinalizedCheckpointIsAncestorOfBlock(
            block.getSlot(), block.getParentRoot()))
        .thenReturn(true);
    when(gossipValidationHelperMock.getSlotForBlockRoot(block.getParentRoot()))
        .thenReturn(Optional.of(nextSlot.decrement()));
    when(gossipValidationHelperMock.isProposerTheExpectedProposer(
            block.getProposerIndex(), block.getSlot(), state))
        .thenReturn(true);
    when(gossipValidationHelperMock.getParentStateInBlockEpoch(
            nextSlot.decrement(), block.getParentRoot(), block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(gossipValidationHelperMock.isBlockAvailable(badBidParentBlockRoot)).thenReturn(true);

    final SignedBeaconBlock invalidBlock =
        createBlockWithModifiedExecutionPayloadBid(
            signedBlockAndState,
            originalExecutionPayloadBid ->
                originalExecutionPayloadBid
                    .getSchema()
                    .create(
                        originalExecutionPayloadBid.getParentBlockHash(),
                        badBidParentBlockRoot,
                        originalExecutionPayloadBid.getBlockHash(),
                        originalExecutionPayloadBid.getPrevRandao(),
                        originalExecutionPayloadBid.getFeeRecipient(),
                        originalExecutionPayloadBid.getGasLimit(),
                        originalExecutionPayloadBid.getBuilderIndex(),
                        originalExecutionPayloadBid.getSlot(),
                        originalExecutionPayloadBid.getValue(),
                        originalExecutionPayloadBid.getExecutionPayment(),
                        originalExecutionPayloadBid.getBlobKzgCommitmentsRoot()));

    assertThat(blockGossipValidatorMocked.validate(invalidBlock, true))
        .isCompletedWithValueMatching(
            result ->
                result.equals(
                    InternalValidationResult.reject(
                        "Execution payload has invalid parent block root %s, expecting %s",
                        badBidParentBlockRoot, expectedParentRoot)));
  }

  private SignedBeaconBlock createBlockWithModifiedExecutionPayloadBid(
      final SignedBlockAndState baseBlockAndState,
      final Function<ExecutionPayloadBid, ExecutionPayloadBid> bidModifier) {
    final SignedBeaconBlock originalSignedBeaconBlock = baseBlockAndState.getBlock();
    final BeaconBlockBody originalBeaconBlockBody =
        originalSignedBeaconBlock.getMessage().getBody();
    final SignedExecutionPayloadBid originalSignedBid =
        originalBeaconBlockBody.getOptionalSignedExecutionPayloadBid().orElseThrow();
    final ExecutionPayloadBid modifiedBid = bidModifier.apply(originalSignedBid.getMessage());
    final SignedExecutionPayloadBid modifiedSignedBid =
        originalSignedBid.getSchema().create(modifiedBid, originalSignedBid.getSignature());

    final BeaconBlockBodyBuilderGloas builder =
        new BeaconBlockBodyBuilderGloas(
            originalBeaconBlockBody.getSchema().toVersionGloas().orElseThrow());
    final BeaconBlockBody modifiedBody =
        builder
            .randaoReveal(originalBeaconBlockBody.getRandaoReveal())
            .eth1Data(originalBeaconBlockBody.getEth1Data())
            .graffiti(originalBeaconBlockBody.getGraffiti())
            .proposerSlashings(originalBeaconBlockBody.getProposerSlashings())
            .attesterSlashings(originalBeaconBlockBody.getAttesterSlashings())
            .attestations(originalBeaconBlockBody.getAttestations())
            .deposits(originalBeaconBlockBody.getDeposits())
            .voluntaryExits(originalBeaconBlockBody.getVoluntaryExits())
            .syncAggregate(originalBeaconBlockBody.getOptionalSyncAggregate().orElseThrow())
            .blsToExecutionChanges(
                originalBeaconBlockBody.getOptionalBlsToExecutionChanges().orElseThrow())
            .payloadAttestations(
                originalBeaconBlockBody.getOptionalPayloadAttestations().orElseThrow())
            .signedExecutionPayloadBid(modifiedSignedBid)
            .build();

    final UInt64 proposerIndex = originalSignedBeaconBlock.getMessage().getProposerIndex();

    final BeaconBlock modifiedUnsignedBlock =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            originalSignedBeaconBlock.getSlot(),
            proposerIndex,
            originalSignedBeaconBlock.getParentRoot(),
            originalSignedBeaconBlock.getStateRoot(),
            modifiedBody);

    final BLSSignature newSignature =
        storageSystem
            .chainBuilder()
            .getSigner(proposerIndex.intValue())
            .signBlock(
                modifiedUnsignedBlock,
                storageSystem.chainBuilder().getLatestBlockAndState().getState().getForkInfo())
            .join();

    return SignedBeaconBlock.create(spec, modifiedUnsignedBlock, newSignature);
  }

  private void assertResultIsAccept(
      final SignedBeaconBlock block, final SafeFuture<InternalValidationResult> result) {
    assertThat(result).isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(receivedBlockEventsChannelPublisher).onBlockValidated(block);
  }
}
