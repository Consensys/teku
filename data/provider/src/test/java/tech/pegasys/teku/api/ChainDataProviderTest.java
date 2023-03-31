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

package tech.pegasys.teku.api;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.migrated.BlockHeadersResponse;
import tech.pegasys.teku.api.migrated.StateSyncCommitteesData;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.BeaconStateBuilderAltair;
import tech.pegasys.teku.spec.util.BeaconStateBuilderCapella;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.ChainHead;
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
  private RecentChainData recentChainData;
  private final CombinedChainDataClient mockCombinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  private final SpecConfig specConfig = spec.getGenesisSpecConfig();

  @BeforeEach
  public void setup() {
    final UInt64 slot = UInt64.valueOf(specConfig.getSlotsPerEpoch() * 3L);
    final UInt64 actualBalance = specConfig.getMaxEffectiveBalance().plus(100000);
    final GenesisStateBuilder genesisBuilder =
        new GenesisStateBuilder().spec(spec).genesisTime(ZERO);
    storageSystem
        .chainBuilder()
        .getValidatorKeys()
        .forEach(key -> genesisBuilder.addValidator(key, actualBalance));
    storageSystem.chainUpdater().initializeGenesis(genesisBuilder.build());
    bestBlock = storageSystem.chainUpdater().advanceChain(slot);
    storageSystem.chainUpdater().updateBestBlock(bestBlock);
    storageSystem.chainUpdater().finalizeEpoch(slot);

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
    final List<ProtoNodeData> chainHeads = provider.getChainHeads();
    assertThat(chainHeads)
        .containsExactly(
            new ProtoNodeData(
                bestBlock.getSlot(),
                blockRoot,
                bestBlock.getParentRoot(),
                bestBlock.getStateRoot(),
                bestBlock.getExecutionBlockHash().orElse(Bytes32.ZERO),
                ProtoNodeValidationStatus.VALID,
                spec.calculateBlockCheckpoints(bestBlock.getState()),
                ZERO));
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
    final UInt64 genesis = beaconStateInternal.getGenesisTime();
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
    final UInt64 genesisTime = beaconStateInternal.getGenesisTime();
    final Bytes32 genesisValidatorsRoot = beaconStateInternal.getGenesisValidatorsRoot();
    final Bytes4 genesisForkVersion = spec.atEpoch(ZERO).getConfig().getGenesisForkVersion();

    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final GenesisData result = provider.getGenesisData();
    assertThat(result)
        .isEqualTo(new GenesisData(genesisTime, genesisValidatorsRoot, genesisForkVersion));
  }

  @Test
  public void getBeaconState_shouldReturnEmptyWhenRootNotFound() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    SafeFuture<Optional<ObjectAndMetaData<BeaconState>>> future =
        provider.getSchemaBeaconState(data.randomBytes32().toHexString());
    assertThatSafeFuture(future).isCompletedWithEmptyOptional();
  }

  @Test
  public void getBeaconState_shouldFindHeadState() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    SafeFuture<Optional<ObjectAndMetaData<BeaconState>>> future =
        provider.getSchemaBeaconState("head");
    final Optional<ObjectAndMetaData<BeaconState>> maybeState = safeJoin(future);
    assertThat(maybeState.orElseThrow().getData().asInternalBeaconState(spec).hashTreeRoot())
        .isEqualTo(beaconStateInternal.hashTreeRoot());
  }

  @Test
  public void getBlockAndMetaDataByBlockId_shouldGetHeadBlock()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final SignedBeaconBlock block =
        storageSystem.getChainHead().getSignedBeaconBlock().orElseThrow();
    BlockAndMetaData result = provider.getBlockAndMetaData("head").get().orElseThrow();

    assertThat(result.getData()).isEqualTo(block);
    assertThat(result.isCanonical()).isTrue();
  }

  @Test
  public void getBlockHeaders_shouldGetHeadBlockIfNoParameters() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final SignedBeaconBlock block =
        storageSystem.getChainHead().getSignedBeaconBlock().orElseThrow();
    BlockHeadersResponse results =
        safeJoin(provider.getBlockHeaders(Optional.empty(), Optional.empty()));
    assertThat(results.getData().get(0).getData().getRoot()).isEqualTo(block.getRoot());
  }

  @Test
  public void getBlockHeaders_shouldGetBlockGivenSlot() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final UInt64 slot = combinedChainDataClient.getCurrentSlot();
    BlockHeadersResponse results =
        safeJoin(provider.getBlockHeaders(Optional.empty(), Optional.of(slot)));
    assertThat(results.getData().get(0).getData().getSlot()).isEqualTo(slot);
  }

  @Test
  public void shouldGetBlockHeadersOnEmptyChainHeadSlot() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final UInt64 headSlot = recentChainData.getHeadSlot();
    storageSystem.chainUpdater().advanceChain(headSlot.plus(1));

    final SafeFuture<BlockHeadersResponse> future =
        provider.getBlockHeaders(Optional.empty(), Optional.empty());
    final BlockAndMetaData header = safeJoin(future).getData().get(0);
    assertThat(header.getData().getSlot()).isEqualTo(headSlot);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorIndex() {

    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(1024);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    List<Integer> indices =
        provider.getFilteredValidatorList(internalState, List.of("1", "33"), emptySet()).stream()
            .map(v -> v.getIndex().intValue())
            .collect(toList());
    assertThat(indices).containsExactly(1, 33);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorPubkey() {
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState(1024);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final Bytes48 key = internalState.getValidators().get(12).getPubkeyBytes();
    final String missingKey = data.randomPublicKey().toString();
    List<Bytes48> pubkeys =
        provider
            .getFilteredValidatorList(
                internalState, List.of(key.toHexString(), missingKey), emptySet())
            .stream()
            .map(v -> v.getValidator().getPublicKey().toBytesCompressed())
            .collect(toList());
    assertThat(pubkeys).containsExactly(key);
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
  public void getStateSyncCommittees_shouldGetCommittees() {
    final ChainDataProvider provider = setupAltairState();
    final List<UInt64> committeeIndices =
        List.of(UInt64.valueOf(6), UInt64.valueOf(9), UInt64.valueOf(0));

    final SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> future =
        provider.getStateSyncCommittees("head", Optional.empty());
    assertThatSafeFuture(future)
        .isCompletedWithOptionalContaining(
            new ObjectAndMetaData<>(
                new StateSyncCommitteesData(committeeIndices, List.of(committeeIndices)),
                SpecMilestone.ALTAIR,
                false,
                true,
                false));
  }

  @Test
  public void getStateSyncCommittees_shouldReturnEmptyListBeforeAltair() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState();
    when(mockCombinedChainDataClient.getBestState())
        .thenReturn(Optional.of(completedFuture(internalState)));

    final SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> future =
        provider.getStateSyncCommittees("head", Optional.empty());
    assertThatSafeFuture(future)
        .isCompletedWithOptionalContaining(
            addMetaData(new StateSyncCommitteesData(List.of(), List.of()), ZERO));
  }

  @Test
  public void getStateSyncCommittees_shouldRejectFarFutureEpoch() {
    final ChainDataProvider provider = setupAltairState();
    final SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> future =
        provider.getStateSyncCommittees("head", Optional.of(UInt64.valueOf("1024000")));
    SafeFutureAssert.assertThatSafeFuture(future)
        .isCompletedExceptionallyWith(IllegalArgumentException.class);
  }

  @Test
  public void getSyncCommitteeRewardsFromBlockId_noSpecifiedValidators() {
    final ChainDataProvider provider = setupAltairState();
    final SafeFuture<Optional<SyncCommitteeRewardData>> future =
        provider.getSyncCommitteeRewardsFromBlockId("head", Set.of());

    final SyncCommitteeRewardData expectedOutput = new SyncCommitteeRewardData(false, false);
    expectedOutput.increaseReward(0, -247L);
    expectedOutput.increaseReward(6, -247L);
    expectedOutput.increaseReward(9, 247L);
    SafeFutureAssert.assertThatSafeFuture(future).isCompletedWithOptionalContaining(expectedOutput);
  }

  @Test
  public void getSyncCommitteeRewardsFromBlockId_specifyValidators() {
    final ChainDataProvider provider = setupAltairState();
    final SafeFuture<Optional<SyncCommitteeRewardData>> future =
        provider.getSyncCommitteeRewardsFromBlockId("head", Set.of("0", "9"));

    final SyncCommitteeRewardData expectedOutput = new SyncCommitteeRewardData(false, false);
    expectedOutput.increaseReward(0, -247L);
    expectedOutput.increaseReward(9, 247L);
    SafeFutureAssert.assertThatSafeFuture(future).isCompletedWithOptionalContaining(expectedOutput);
  }

  @Test
  public void getSyncCommitteeRewardsFromBlockId_emptyBlockAndMetaData() {
    final ChainDataProvider provider = setupAltairState();
    when(mockCombinedChainDataClient.getChainHead()).thenReturn(Optional.empty());

    final SafeFuture<Optional<SyncCommitteeRewardData>> future =
        provider.getSyncCommitteeRewardsFromBlockId("head", Set.of());
    SafeFutureAssert.assertThatSafeFuture(future).isCompletedWithEmptyOptional();
  }

  @Test
  public void getSyncCommitteeRewardsFromBlockId_slotIsPreAltair() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final SafeFuture<Optional<SyncCommitteeRewardData>> future =
        provider.getSyncCommitteeRewardsFromBlockId("head", Set.of());
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(BadRequestException.class);
    assertThatThrownBy(future::get)
        .hasMessageMatching(
            "tech.pegasys.teku.api.exceptions.BadRequestException: "
                + "Slot [0-9]+ is pre altair, and no sync committee information is available");
  }

  @Test
  public void getCommitteeIndices_withSpecifiedValidators() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state =
        data.randomBeaconState(32);

    final List<BLSPublicKey> stateValidators =
        state.getValidators().stream().map(Validator::getPublicKey).collect(toList());
    final List<BLSPublicKey> committeeKeys =
        List.of(
            stateValidators.get(1),
            stateValidators.get(4),
            stateValidators.get(6),
            stateValidators.get(7),
            stateValidators.get(9),
            stateValidators.get(11));

    final Set<String> validators =
        Set.of(committeeKeys.get(0).toHexString(), committeeKeys.get(2).toHexString(), "4");
    final Map<Integer, Integer> committeeIndices =
        provider.getCommitteeIndices(committeeKeys, validators, state);

    assertThat(committeeIndices).containsExactlyInAnyOrderEntriesOf(Map.of(0, 1, 1, 4, 2, 6));
  }

  @Test
  public void getCommitteeIndices_withNoSpecifiedValidators() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state =
        data.randomBeaconState(32);

    final List<BLSPublicKey> stateValidators =
        state.getValidators().stream().map(Validator::getPublicKey).collect(toList());
    final List<BLSPublicKey> committeeKeys =
        List.of(
            stateValidators.get(1),
            stateValidators.get(4),
            stateValidators.get(6),
            stateValidators.get(7),
            stateValidators.get(9),
            stateValidators.get(11));

    final Map<Integer, Integer> committeeIndices =
        provider.getCommitteeIndices(committeeKeys, Set.of(), state);

    assertThat(committeeIndices)
        .containsExactlyInAnyOrderEntriesOf(Map.of(0, 1, 1, 4, 2, 6, 3, 7, 4, 9, 5, 11));
  }

  @Test
  public void getCommitteeIndices_withSpecifiedValidatorsNotInCommittee() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state =
        data.randomBeaconState(32);

    final List<BLSPublicKey> stateValidators =
        state.getValidators().stream().map(Validator::getPublicKey).collect(toList());
    final List<BLSPublicKey> committeeKeys =
        List.of(
            stateValidators.get(1),
            stateValidators.get(4),
            stateValidators.get(6),
            stateValidators.get(7),
            stateValidators.get(9),
            stateValidators.get(11));

    final Set<String> validators =
        Set.of(
            data.randomPublicKey().toHexString(), // Validator not in committee
            "2", // Validator not in committee
            committeeKeys.get(5).toHexString());

    assertThatThrownBy(() -> provider.getCommitteeIndices(committeeKeys, validators, state))
        .isInstanceOf(BadRequestException.class)
        .hasMessageMatching(
            "'0xab26a22f1c8c779103154eb379a79af4928383e95d6d827a2bddd6263af6c3d9ae4be8e2949fd4827964b22b72368069' "
                + "is not a valid hex encoded public key or validator index in the committee");
  }

  @Test
  public void calculateRewards_shouldGetData() {
    final long reward = 5000L;
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();

    final Map<Integer, Integer> committeeIndices = Map.of(0, 4, 1, 2);
    final SyncCommitteeRewardData inputData = new SyncCommitteeRewardData(true, false);
    final SyncCommitteeRewardData syncCommitteeRewardData =
        provider.calculateRewards(committeeIndices, reward, data.randomBeaconBlock(), inputData);

    assertThat(syncCommitteeRewardData.isExecutionOptimistic()).isTrue();
    assertThat(syncCommitteeRewardData.isFinalized()).isFalse();
    assertThat(syncCommitteeRewardData.getRewardData())
        .containsExactlyInAnyOrder(Map.entry(2, -1 * reward), Map.entry(4, reward));
  }

  @Test
  public void calculateProposerSyncAggregateBlockRewards_manySyncAggregateIndices() {
    final UInt64 reward = UInt64.valueOf(1234);
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();
    final int[] participantIndices = new int[] {0, 3, 4, 7, 16, 17, 20, 23, 25, 26, 29, 30};
    final SyncAggregate syncAggregate = data.randomSyncAggregate(participantIndices);

    final int syncAggregateBlockRewards =
        provider.calculateProposerSyncAggregateBlockRewards(reward, syncAggregate);
    assertThat(syncAggregateBlockRewards)
        .isEqualTo(reward.times(participantIndices.length).intValue());
  }

  @Test
  public void calculateProposerSyncAggregateBlockRewards_emptySyncAggregate() {
    final UInt64 reward = UInt64.valueOf(1234);
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();
    final SyncAggregate syncAggregate = data.emptySyncAggregate();

    final int syncAggregateBlockRewards =
        provider.calculateProposerSyncAggregateBlockRewards(reward, syncAggregate);
    assertThat(syncAggregateBlockRewards).isEqualTo(0);
  }

  @Test
  public void calculateProposerSlashingsRewards_shouldCalculateRewards() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();
    final BeaconBlockAndState blockAndState = data.randomBlockAndState(100);

    final UInt64 result =
        provider.calculateProposerSlashingsRewards(
            blockAndState.getBlock(), blockAndState.getState());
    final UInt64 expectedReward = UInt64.valueOf(62500000);
    assertThat(result).isEqualTo(expectedReward);
  }

  @Test
  public void calculateAttesterSlashingsRewards_shouldCalculateRewards() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();
    final BeaconBlockAndState blockAndState = data.randomBlockAndState(100);

    final UInt64 result =
        provider.calculateAttesterSlashingsRewards(
            blockAndState.getBlock(), blockAndState.getState());
    final UInt64 expectedReward = UInt64.valueOf(62500000);
    assertThat(result).isEqualTo(expectedReward);
  }

  @Test
  public void calculateAttestationRewards_shouldCalculateRewards() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupAltairState();
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state =
        data.randomBeaconState(100);

    final UInt64 result = provider.calculateAttestationRewards(state);
    assertThat(result).isNotEqualTo(ZERO); // TODO fix to check real reward
  }

  @Test
  public void getLightClientBootstrap_shouldGetBootstrap() {
    final ChainDataProvider provider = setupAltairState();
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        getHeadState();

    BeaconBlockHeader expectedBlockHeader = BeaconBlockHeader.fromState(internalState);

    when(mockCombinedChainDataClient.getStateByBlockRoot(eq(expectedBlockHeader.getRoot())))
        .thenReturn(completedFuture(Optional.of(internalState)));

    final SafeFuture<Optional<ObjectAndMetaData<LightClientBootstrap>>> future =
        provider.getLightClientBoostrap(expectedBlockHeader.getRoot());

    LightClientBootstrap bootstrap = safeJoin(future).orElseThrow().getData();

    assertThat(bootstrap.get(0)).isEqualTo(expectedBlockHeader);
    assertThat(bootstrap.get(1))
        .isEqualTo(BeaconStateAltair.required(internalState).getCurrentSyncCommittee());
  }

  @Test
  public void getLightClientBootstrap_shouldReturnEmptyWhenBlockNotFound() {
    final ChainDataProvider provider = setupAltairState();
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        getHeadState();

    BeaconBlockHeader expectedBlockHeader = BeaconBlockHeader.fromState(internalState);

    when(mockCombinedChainDataClient.getStateByBlockRoot(any()))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<ObjectAndMetaData<LightClientBootstrap>>> future =
        provider.getLightClientBoostrap(expectedBlockHeader.getRoot());
    assertThatSafeFuture(future).isCompletedWithEmptyOptional();
  }

  @Test
  public void getLightClientBootstrap_shouldReturnEmptyBeforeAltair() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState internalState =
        data.randomBeaconState();

    BeaconBlockHeader expectedBlockHeader = BeaconBlockHeader.fromState(internalState);

    when(mockCombinedChainDataClient.getStateByBlockRoot(eq(expectedBlockHeader.getRoot())))
        .thenReturn(completedFuture(Optional.of(internalState)));

    final SafeFuture<Optional<ObjectAndMetaData<LightClientBootstrap>>> future =
        provider.getLightClientBoostrap(expectedBlockHeader.getRoot());
    assertThatSafeFuture(future).isCompletedWithEmptyOptional();
  }

  @Test
  public void getStateFork_shouldGetForkAtGenesis() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final Bytes4 bytes4 = Bytes4.fromHexString("0x00000001");
    final SafeFuture<Optional<ObjectAndMetaData<Fork>>> result = provider.getStateFork("genesis");
    assertThatSafeFuture(result)
        .isCompletedWithOptionalContaining(addMetaData(new Fork(bytes4, bytes4, ZERO), ZERO));
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
    final Optional<ObjectAndMetaData<Bytes32>> response = provider.getBlockRoot("head").get();
    assertThat(response).isPresent();
    assertThat(response.get().getData()).isEqualTo(bestBlock.getRoot());
  }

  @Test
  public void getBlockAttestations_shouldReturnAttestationsOfBlock() throws Exception {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    ChainBuilder chainBuilder = storageSystem.chainBuilder();

    ChainBuilder.BlockOptions blockOptions = ChainBuilder.BlockOptions.create();
    AttestationGenerator attestationGenerator =
        new AttestationGenerator(spec, chainBuilder.getValidatorKeys());
    Attestation attestation1 =
        attestationGenerator.validAttestation(bestBlock.toUnsigned(), bestBlock.getSlot());
    Attestation attestation2 =
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

    final Optional<ObjectAndMetaData<List<Attestation>>> response =
        provider.getBlockAttestations("head").get();
    assertThat(response).isPresent();
    assertThat(response.get().getData()).containsExactly(attestation1, attestation2);
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

  @Test
  void getValidatorInclusionAtEpoch_shouldThrowServiceUnavailableIfEpochNotFound() {
    final RecentChainData recentChainData1 = mock(RecentChainData.class);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData1, combinedChainDataClient);
    when(recentChainData1.getCurrentEpoch()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> provider.getValidatorInclusionAtEpoch(data.randomEpoch()))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void getValidatorInclusionAtEpoch_shouldThrowIllegalArgForCurrentOrFutureEpoch() {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    assertThatThrownBy(() -> provider.getValidatorInclusionAtEpoch(UInt64.valueOf(3)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> provider.getValidatorInclusionAtEpoch(UInt64.valueOf(4)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> provider.getValidatorInclusionAtEpoch(UInt64.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getValidatorInclusionAtEpoch_shouldReturnStateForPreviousEpoch()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, mockCombinedChainDataClient);
    // expect to see the last slot of epoch requested, so 3 * 8 - 1 (23)
    when(mockCombinedChainDataClient.getChainHead())
        .thenReturn(combinedChainDataClient.getChainHead());
    when(mockCombinedChainDataClient.getStateAtSlotExact(eq(UInt64.valueOf(23)), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    provider.getValidatorInclusionAtEpoch(UInt64.valueOf(2)).get();
    verify(mockCombinedChainDataClient).getStateAtSlotExact(eq(UInt64.valueOf(23)), any());
  }

  @Test
  void getFinalizedBlockRoot_shouldReturnBlockRootWhenFinalized()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    Optional<Bytes32> finalizedBlockRoot = provider.getFinalizedBlockRoot(UInt64.valueOf(24)).get();
    assertThat(finalizedBlockRoot).isPresent();
  }

  @Test
  void getFinalizedBlockRoot_shouldReturnEmptyWhenBlockNotFinalized()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    Optional<Bytes32> finalizedBlockRoot = provider.getFinalizedBlockRoot(UInt64.valueOf(1)).get();
    assertThat(finalizedBlockRoot).isEmpty();
  }

  @Test
  void getExpectedWithdrawalsFailsForHistoricRequest() {
    final Spec capella = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    assertThatThrownBy(
            () ->
                chainDataProvider.getExpectedWithdrawalsFromState(
                    dataStructureUtil.randomBeaconState(UInt64.ONE), Optional.of(UInt64.ONE)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("historic");
  }

  @Test
  void getExpectedWithdrawalsFailsPreCapella() {
    final ChainDataProvider chainDataProvider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    assertThatThrownBy(
            () ->
                chainDataProvider.getExpectedWithdrawalsFromState(
                    data.randomBeaconState(UInt64.ONE), Optional.empty()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("pre-capella");
  }

  @Test
  void getExpectedWithdrawalsFailsTooFarInFutureRequest() {
    final Spec capella = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    assertThatThrownBy(
            () ->
                chainDataProvider.getExpectedWithdrawalsFromState(
                    dataStructureUtil.randomBeaconState(UInt64.ZERO),
                    Optional.of(UInt64.valueOf(129))))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("more than 128 slots ahead");
  }

  @Test
  void getExpectedWithdrawalsGeneratesList() {
    final Spec capella = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    final BeaconStateBuilderCapella capellaBuilder = dataStructureUtil.stateBuilderCapella();

    capellaBuilder.slot(UInt64.valueOf(1024_000));
    capellaBuilder.nextWithdrawalIndex(UInt64.valueOf(10));
    final List<Validator> validators = new ArrayList<>();
    while (validators.size() < 16) {
      validators.add(
          dataStructureUtil.randomValidator(
              dataStructureUtil.randomPublicKey(),
              dataStructureUtil.randomEth1WithdrawalCredentials(),
              specConfig.getMaxEffectiveBalance()));
    }
    final UInt64 eff = specConfig.getMaxEffectiveBalance();
    capellaBuilder.balances(
        eff.plus(1024_000),
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff.plus(1),
        eff,
        eff,
        eff,
        eff);
    capellaBuilder.validators(
        dataStructureUtil
            .getBeaconStateSchema()
            .getValidatorsSchema()
            .createFromElements(validators));

    final List<Withdrawal> withdrawals =
        chainDataProvider.getExpectedWithdrawalsFromState(capellaBuilder.build(), Optional.empty());

    assertThat(withdrawals).hasSize(2);
    assertThat(withdrawals.get(0).getValidatorIndex()).isEqualTo(UInt64.valueOf(11));
    assertThat(withdrawals.get(0).getAddress())
        .isEqualTo(Bytes20.fromHexString("0xaa8683942152c67b23383b1b6d351b3cbd3df4a7"));
    assertThat(withdrawals.get(0).getAmount()).isEqualTo(UInt64.valueOf(1));
    assertThat(withdrawals.get(1).getValidatorIndex()).isEqualTo(UInt64.valueOf(0));
    assertThat(withdrawals.get(1).getAddress())
        .isEqualTo(Bytes20.fromHexString("0xba2a1c95e2a3187d3b6bbeb2ef4f05681bef0dd4"));
    assertThat(withdrawals.get(1).getAmount()).isEqualTo(UInt64.valueOf(1024_000));
  }

  @ParameterizedTest(name = "given slot={0} when epoch={1} then randao={2}")
  @MethodSource("getRandaoIndexCases")
  void getRandaoIndex(
      final int stateSlot, final int queryEpoch, final Optional<Bytes32> maybeRandao) {
    final tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState state =
        data.randomBeaconState(UInt64.valueOf(stateSlot));
    final UInt64 epoch = UInt64.valueOf(queryEpoch);
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);
    assertThat(provider.getRandaoAtEpochFromState(state, Optional.of(epoch)))
        .isEqualTo(maybeRandao);
  }

  public static Stream<Arguments> getRandaoIndexCases() {
    final int epochsPerHistoricalVector = 64;
    final int slotsPerEpoch = 8;
    final int currentEpoch = 2048;
    final int stateSlot = currentEpoch * slotsPerEpoch; // first slot of epoch 100
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(stateSlot, currentEpoch - epochsPerHistoricalVector, Optional.empty()));
    args.add(Arguments.of(stateSlot, currentEpoch + 1, Optional.empty()));
    final Optional<Bytes32> randao =
        Optional.of(
            Bytes32.fromHexString(
                "0xcc9b03923c85d9ce8eba32968977d98d668788d1c2f0e4f3a79facfcd8247794"));
    final Optional<Bytes32> randao2 =
        Optional.of(
            Bytes32.fromHexString(
                "0x933815925ca23b00aef1fc130edd9b26547e8b1b9f966ac51e1ce2a3480251f4"));
    args.add(Arguments.of(stateSlot, currentEpoch - epochsPerHistoricalVector + 1, randao2));
    args.add(Arguments.of(stateSlot, currentEpoch, randao));
    args.add(Arguments.of(7, 0, randao));
    args.add(Arguments.of(9, 0, randao));
    args.add(Arguments.of(9, 1, randao2));
    return args.stream();
  }

  private ChainDataProvider setupAltairState() {
    final Spec altair = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(altair);
    return setupBySpec(altair, dataStructureUtil, 16);
  }

  private ChainDataProvider setupBySpec(
      final Spec spec, final DataStructureUtil dataStructureUtil, final int validatorCount) {
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, mockCombinedChainDataClient);

    if (spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      final SszList<Validator> validators =
          dataStructureUtil.randomSszList(
              dataStructureUtil.getBeaconStateSchema().getValidatorsSchema(),
              validatorCount,
              dataStructureUtil::randomValidator);
      final SyncCommittee currentSyncCommittee = dataStructureUtil.randomSyncCommittee(validators);

      final BeaconStateBuilderAltair builder =
          dataStructureUtil
              .stateBuilderAltair()
              .validators(validators)
              .currentSyncCommittee(currentSyncCommittee);
      final SignedBlockAndState signedBlockAndState =
          dataStructureUtil.randomSignedBlockAndState(builder.build());
      final ChainHead chainHead =
          ChainHead.create(StateAndBlockSummary.create(signedBlockAndState));
      when(mockCombinedChainDataClient.getChainHead()).thenReturn(Optional.of(chainHead));
      when(mockCombinedChainDataClient.getStateByBlockRoot(
              eq(signedBlockAndState.getBlock().getRoot())))
          .thenReturn(SafeFuture.completedFuture(Optional.of(signedBlockAndState.getState())));
    }

    return provider;
  }

  private tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState getHeadState() {
    return safeJoin(mockCombinedChainDataClient.getChainHead().orElseThrow().getState());
  }

  private <T> ObjectAndMetaData<T> addMetaData(final T expected, final UInt64 slot) {
    return new ObjectAndMetaData<>(
        expected,
        spec.atSlot(slot).getMilestone(),
        false,
        true,
        combinedChainDataClient.isFinalized(slot));
  }
}
