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

package tech.pegasys.teku.api;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.v1.beacon.BlockHeader;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.StateSyncCommittees;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.response.v1.debug.ChainHead;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.Root;
import tech.pegasys.teku.api.schema.SignedBeaconBlockHeader;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ChainDataProviderTest {
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private CombinedChainDataClient combinedChainDataClient;
  private tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState beaconStateInternal;

  private SignedBlockAndState bestBlock;
  private Bytes32 blockRoot;
  private UInt64 slot;
  private RecentChainData recentChainData;
  private final CombinedChainDataClient mockCombinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private UInt64 actualBalance;
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  private final SpecConfig specConfig = spec.getGenesisSpecConfig();

  @BeforeEach
  public void setup() {
    slot = UInt64.valueOf(specConfig.getSlotsPerEpoch() * 3L);
    actualBalance = specConfig.getMaxEffectiveBalance().plus(100000);
    storageSystem.chainUpdater().initializeGenesis(true, actualBalance, Optional.empty());
    bestBlock = storageSystem.chainUpdater().advanceChain(slot);
    storageSystem.chainUpdater().updateBestBlock(bestBlock);

    recentChainData = storageSystem.recentChainData();
    beaconStateInternal = bestBlock.getState();

    combinedChainDataClient = storageSystem.combinedChainDataClient();
    blockRoot = bestBlock.getRoot();
  }

  @Test
  public void getChainHeads_shouldReturnChainHeads()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final SafeFuture<Optional<List<ChainHead>>> future = provider.getChainHeads();
    final Optional<List<ChainHead>> maybeResult = future.get();
    assertThat(maybeResult.orElse(emptyList()))
        .containsExactly(new ChainHead(bestBlock.getSlot(), blockRoot));
  }

  @Test
  public void getGenesisTime_shouldThrowIfStoreNotAvailable() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, null, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    assertThatThrownBy(provider::getGenesisTime).isInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getGenesisTime_shouldReturnValueIfStoreAvailable() {
    final UInt64 genesis = beaconStateInternal.getGenesis_time();
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final UInt64 result = provider.getGenesisTime();
    assertEquals(genesis, result);
  }

  @Test
  public void getGenesisData_shouldThrowIfStoreNotAvailable() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, null, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    assertThatThrownBy(provider::getGenesisData).isInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getGenesisData_shouldReturnValueIfStoreAvailable() {
    final UInt64 genesisTime = beaconStateInternal.getGenesis_time();
    final Bytes32 genesisValidatorsRoot = beaconStateInternal.getGenesis_validators_root();
    final Bytes4 genesisForkVersion = spec.atEpoch(ZERO).getConfig().getGenesisForkVersion();

    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final GenesisData result = provider.getGenesisData();
    assertThat(result)
        .isEqualTo(new GenesisData(genesisTime, genesisValidatorsRoot, genesisForkVersion));
  }

  @Test
  public void getBeaconState_shouldReturnEmptyWhenRootNotFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    SafeFuture<Optional<BeaconState>> future =
        provider.getBeaconState(data.randomBytes32().toHexString());
    final Optional<BeaconState> maybeState = future.get();
    assertThat(maybeState).isEmpty();
  }

  @Test
  public void getBeaconState_shouldFindHeadState() throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    SafeFuture<Optional<BeaconState>> future = provider.getBeaconState("head");
    final Optional<BeaconState> maybeState = future.get();
    assertThat(maybeState.get().asInternalBeaconState(spec).hashTreeRoot())
        .isEqualTo(beaconStateInternal.hashTreeRoot());
  }

  @Test
  public void validatorParameterToIndex_shouldThrowWhenStoreNotFound() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, null, mockCombinedChainDataClient);
    assertThrows(
        ChainDataUnavailableException.class, () -> provider.validatorParameterToIndex("1"));
  }

  @Test
  public void validatorParameterToIndex_shouldAcceptValidatorRoot() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    Validator validator =
        new Validator(recentChainData.getBestState().get().getValidators().get(1));

    assertThat(provider.validatorParameterToIndex(validator.pubkey.toHexString()))
        .isEqualTo(Optional.of(1));
  }

  @Test
  public void validatorParameterToIndex_shouldAcceptValidatorId() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    assertThat(provider.validatorParameterToIndex("2")).isEqualTo(Optional.of(2));
  }

  @Test
  public void validatorParameterToIndex_shouldThrowException() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    assertThrows(BadRequestException.class, () -> provider.validatorParameterToIndex("2a"));
  }

  @Test
  public void validatorParameterToIndex_shouldDetectAboveMaxInt() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    assertThrows(
        BadRequestException.class,
        () ->
            provider.validatorParameterToIndex(
                UInt64.valueOf(Integer.MAX_VALUE).increment().toString()));
  }

  @Test
  public void validatorParameterToIndex_shouldThrowExceptionWithInvalidPublicKey() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    assertThrows(
        BadRequestException.class,
        () -> provider.validatorParameterToIndex(Bytes32.EMPTY.toHexString()));
  }

  @Test
  public void getBlockHeaderByBlockId_shouldGetHeadBlock()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock block =
        combinedChainDataClient.getBestBlock().get();
    BlockHeader result = provider.getBlockHeader("head").get().get();
    final BeaconBlockHeader beaconBlockHeader =
        new BeaconBlockHeader(
            block.getSlot(),
            block.getMessage().getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            block.getRoot());
    final BlockHeader expected =
        new BlockHeader(
            block.getRoot(),
            true,
            new SignedBeaconBlockHeader(beaconBlockHeader, new BLSSignature(block.getSignature())));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void getStateRoot_shouldGetRootAtGenesis()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final Optional<tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState> state =
        combinedChainDataClient.getStateAtSlotExact(ZERO).get();
    final Optional<Root> maybeStateRoot = provider.getStateRoot("genesis").get();
    assertThat(maybeStateRoot).isPresent();
    assertThat(maybeStateRoot.orElseThrow().root).isEqualTo(state.orElseThrow().hashTreeRoot());
  }

  @Test
  public void getBlockHeaders_shouldGetHeadBlockIfNoParameters()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock block =
        combinedChainDataClient.getBestBlock().get();
    List<BlockHeader> results = provider.getBlockHeaders(Optional.empty(), Optional.empty()).get();
    assertThat(results.get(0).root).isEqualTo(block.getRoot());
  }

  @Test
  public void getBlockHeaders_shouldGetBlockGivenSlot()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final UInt64 slot = combinedChainDataClient.getCurrentSlot();
    List<BlockHeader> results = provider.getBlockHeaders(Optional.empty(), Optional.of(slot)).get();
    assertThat(results.get(0).header.message.slot).isEqualTo(slot);
  }

  @Test
  public void shouldGetBlockHeadersOnEmptyChainHeadSlot() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final UInt64 headSlot = recentChainData.getHeadSlot();
    storageSystem.chainUpdater().advanceChain(headSlot.plus(1));

    final SafeFuture<List<BlockHeader>> future =
        provider.getBlockHeaders(Optional.empty(), Optional.empty());
    final BlockHeader header = future.join().get(0);
    assertThat(header.header.message.slot).isEqualTo(headSlot);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorIndex() {

    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(1024);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    List<Integer> indexes =
        provider.getFilteredValidatorList(internalState, List.of("1", "33"), emptySet()).stream()
            .map(v -> v.index.intValue())
            .collect(toList());
    assertThat(indexes).containsExactly(1, 33);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorPubkey() {
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(1024);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final String key = internalState.getValidators().get(12).getPubkeyBytes().toString();
    final String missingKey = data.randomPublicKey().toString();
    List<String> pubkeys =
        provider
            .getFilteredValidatorList(internalState, List.of(key, missingKey), emptySet())
            .stream()
            .map(v -> v.validator.pubkey.toHexString())
            .collect(toList());
    assertThat(pubkeys).containsExactly(key);
  }

  @Test
  public void validatorParameterToIndex_shouldThrowBadRequestExceptionWhenIndexInvalid() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    assertThrows(BadRequestException.class, () -> provider.validatorParameterToIndex("a"));
  }

  @Test
  public void validatorParameterToIndex_shouldReturnEmptyIfIndexOutOfBounds() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    assertThat(provider.validatorParameterToIndex("1024000")).isEmpty();
  }

  @Test
  public void validatorParameterToIndex_shouldThrowBadRequestExceptionWhenKeyNotFound() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    assertThrows(
        BadRequestException.class,
        () -> provider.validatorParameterToIndex(Bytes32.fromHexString("0x00").toHexString()));
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorStatus() {
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(11);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    assertThat(
            provider.getFilteredValidatorList(
                internalState, emptyList(), Set.of(ValidatorStatus.pending_initialized)))
        .hasSize(11);
    assertThat(
            provider.getFilteredValidatorList(
                internalState, emptyList(), Set.of(ValidatorStatus.active_ongoing)))
        .hasSize(0);
  }

  @Test
  public void getStateCommittees_shouldReturnEmptyIfStateNotFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    assertThat(
            provider
                .getStateCommittees(
                    data.randomBytes32().toHexString(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())
                .get())
        .isEmpty();
  }

  @Test
  public void getCommitteesFromState_shouldNotRequireFilters() {
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(64);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    assertThat(
            provider
                .getCommitteesFromState(
                    internalState, Optional.empty(), Optional.empty(), Optional.empty())
                .size())
        .isEqualTo(specConfig.getSlotsPerEpoch());
  }

  @Test
  public void getCommitteesFromState_shouldFilterOnSlot() {
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(64);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    assertThat(
            provider
                .getCommitteesFromState(
                    internalState,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(internalState.getSlot()))
                .size())
        .isEqualTo(1);
  }

  @Test
  public void getStateFinalityCheckpoints_shouldGetEmptyCheckpointsBeforeFinalized()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    assertThat(provider.getStateFinalityCheckpoints("genesis").get().get())
        .isEqualTo(
            new FinalityCheckpointsResponse(
                tech.pegasys.teku.api.schema.Checkpoint.EMPTY,
                tech.pegasys.teku.api.schema.Checkpoint.EMPTY,
                tech.pegasys.teku.api.schema.Checkpoint.EMPTY));
  }

  @Test
  public void getStateFinalityCheckpoints_shouldGetCheckpointsAfterFinalized()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, mockCombinedChainDataClient);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(UInt64.valueOf(42));
    final FinalityCheckpointsResponse expected =
        new FinalityCheckpointsResponse(
            new tech.pegasys.teku.api.schema.Checkpoint(
                internalState.getPrevious_justified_checkpoint()),
            new tech.pegasys.teku.api.schema.Checkpoint(
                internalState.getCurrent_justified_checkpoint()),
            new tech.pegasys.teku.api.schema.Checkpoint(internalState.getFinalized_checkpoint()));

    when(mockCombinedChainDataClient.getBestState()).thenReturn(Optional.of(internalState));
    assertThat(provider.getStateFinalityCheckpoints("head").get().get()).isEqualTo(expected);
    verify(mockCombinedChainDataClient).getBestState();
  }

  @Test
  public void getStateSyncCommittees_shouldGetCommittees()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider = setupAltairState();
    final List<UInt64> committeeIndices =
        List.of(UInt64.valueOf(6), UInt64.valueOf(9), UInt64.valueOf(0));

    final SafeFuture<Optional<StateSyncCommittees>> future =
        provider.getStateSyncCommittees("head", Optional.empty());
    assertThat(future).isCompleted();
    assertThat(future.get().get())
        .isEqualTo(new StateSyncCommittees(committeeIndices, List.of(committeeIndices)));
  }

  @Test
  public void getStateSyncCommittees_shouldReturnEmptyListBeforeAltair()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState();
    when(mockCombinedChainDataClient.getBestState()).thenReturn(Optional.of(internalState));

    final SafeFuture<Optional<StateSyncCommittees>> future =
        provider.getStateSyncCommittees("head", Optional.empty());
    assertThat(future.get().get()).isEqualTo(new StateSyncCommittees(List.of(), List.of()));
  }

  @Test
  public void getStateSyncCommittees_shouldRejectFarFutureEpoch() {
    final ChainDataProvider provider = setupAltairState();
    final SafeFuture<Optional<StateSyncCommittees>> future =
        provider.getStateSyncCommittees("head", Optional.of(UInt64.valueOf("1024000")));
    SafeFutureAssert.assertThatSafeFuture(future)
        .isCompletedExceptionallyWith(IllegalArgumentException.class);
  }

  @Test
  public void getStateFork_shouldGetForkAtGenesis()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final Bytes4 bytes4 = Bytes4.fromHexString("0x00000001");
    final Optional<Fork> response = provider.getStateFork("genesis").get();
    assertThat(response).isPresent();
    assertThat(response.get()).isEqualTo(new Fork(bytes4, bytes4, ZERO));
  }

  @Test
  public void getValidatorBalancesFromState_shouldGetBalances() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(1024);
    assertThat(provider.getValidatorBalancesFromState(internalState, emptyList())).hasSize(1024);

    assertThat(
            provider.getValidatorBalancesFromState(
                internalState, List.of("0", "100", "1023", "1024", "1024000")))
        .hasSize(3);
  }

  @Test
  public void getBlockRoot_shouldReturnRootOfBlock() throws Exception {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final Optional<Root> response = provider.getBlockRoot("head").get();
    assertThat(response).isPresent();
    assertThat(response.get()).isEqualTo(new Root(bestBlock.getRoot()));
  }

  @Test
  public void getBlockAttestations_shouldReturnAttestationsOfBlock() throws Exception {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    ChainBuilder chainBuilder = storageSystem.chainBuilder();

    ChainBuilder.BlockOptions blockOptions = ChainBuilder.BlockOptions.create();
    AttestationGenerator attestationGenerator =
        new AttestationGenerator(spec, chainBuilder.getValidatorKeys());
    tech.pegasys.teku.spec.datastructures.operations.Attestation attestation1 =
        attestationGenerator.validAttestation(bestBlock.toUnsigned(), bestBlock.getSlot());
    tech.pegasys.teku.spec.datastructures.operations.Attestation attestation2 =
        attestationGenerator.validAttestation(
            bestBlock.toUnsigned(), bestBlock.getSlot().increment());
    blockOptions.addAttestation(attestation1);
    blockOptions.addAttestation(attestation2);
    SignedBlockAndState newHead =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(bestBlock.getSlot().plus(10), blockOptions);
    storageSystem.chainUpdater().saveBlock(newHead);
    storageSystem.chainUpdater().updateBestBlock(newHead);

    final Optional<List<Attestation>> response = provider.getBlockAttestations("head").get();
    assertThat(response).isPresent();
    assertThat(response.get())
        .containsExactly(new Attestation(attestation1), new Attestation(attestation2));
  }

  @Test
  void pathParamMaySupportAltair_shouldBeFalseIfAltairNotSupported() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    assertThat(provider.stateParameterMaySupportAltair("genesis")).isFalse();
  }

  @Test
  void pathParamMaySupportAltair_shouldBeTrueIfAltairSupported() {
    final ChainDataProvider provider = setupAltairState();
    assertThat(provider.stateParameterMaySupportAltair("genesis")).isTrue();
    assertThat(provider.stateParameterMaySupportAltair("1")).isTrue();
    assertThat(provider.stateParameterMaySupportAltair("0x00")).isTrue();
  }

  private ChainDataProvider setupAltairState() {
    final Spec altair = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(altair);
    final ChainDataProvider provider =
        new ChainDataProvider(altair, recentChainData, mockCombinedChainDataClient);

    final SszList<tech.pegasys.teku.spec.datastructures.state.Validator> validators =
        dataStructureUtil.randomSszList(
            dataStructureUtil.getBeaconStateSchema().getValidatorsSchema(),
            16,
            dataStructureUtil::randomValidator);
    final SyncCommittee currentSyncCommittee = dataStructureUtil.randomSyncCommittee(validators);

    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        dataStructureUtil
            .stateBuilderAltair()
            .validators(validators)
            .currentSyncCommittee(currentSyncCommittee)
            .build();
    when(mockCombinedChainDataClient.getBestState()).thenReturn(Optional.of(internalState));
    return provider;
  }
}
