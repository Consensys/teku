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

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorWithIndex;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.async.SafeFuture;

public class ChainDataProviderTest {
  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private static CombinedChainDataClient combinedChainDataClient;
  private static StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private static tech.pegasys.teku.datastructures.state.BeaconState beaconStateInternal;
  private static BeaconState beaconState;
  private static Bytes32 blockRoot;
  private static UnsignedLong slot;
  private static EventBus localEventBus;
  private static RecentChainData recentChainData;
  private final tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock signedBeaconBlock =
      dataStructureUtil.randomSignedBeaconBlock(999);
  private CombinedChainDataClient mockCombinedChainDataClient = mock(CombinedChainDataClient.class);
  private RecentChainData mockRecentChainData = mock(RecentChainData.class);

  @BeforeAll
  public static void setup() {
    localEventBus = new EventBus();
    recentChainData = MemoryOnlyRecentChainData.create(localEventBus);
    beaconStateInternal = dataStructureUtil.randomBeaconState();

    beaconState = new BeaconState(beaconStateInternal);
    recentChainData.initializeFromGenesis(beaconStateInternal);
    combinedChainDataClient = new CombinedChainDataClient(recentChainData, historicalChainData);
    blockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    slot = recentChainData.getBestSlot();
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnEmptyListWhenStateAtSlotIsNotFound()
      throws Exception {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final SafeFuture<Optional<List<Committee>>> future =
        provider.getCommitteesAtEpoch(UnsignedLong.valueOf(50));
    assertThat(future.get()).isEmpty();
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnEmptyListWhenAFutureEpochIsRequested()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final UnsignedLong futureEpoch = slot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH));

    final SafeFuture<Optional<List<Committee>>> future = provider.getCommitteesAtEpoch(futureEpoch);
    assertThat(future.get()).isEmpty();
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnAListOfCommittees()
      throws ExecutionException, InterruptedException {
    final List<CommitteeAssignment> committeeAssignments =
        List.of(new CommitteeAssignment(List.of(1), ZERO, ONE));
    final ChainDataProvider provider =
        new ChainDataProvider(mockRecentChainData, mockCombinedChainDataClient);
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final BeaconBlockAndState blockAndState =
        dataStructureUtil.randomBlockAndState(1, beaconStateInternal);

    when(mockCombinedChainDataClient.isChainDataFullyAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBestBlockRoot()).thenReturn(Optional.of(bestBlockRoot));
    when(mockCombinedChainDataClient.getCommitteesFromState(any(), any()))
        .thenReturn(committeeAssignments);
    when(mockRecentChainData.getBestSlot()).thenReturn(beaconStateInternal.getSlot());
    when(mockCombinedChainDataClient.getBlockAndStateInEffectAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState)));
    final SafeFuture<Optional<List<Committee>>> future =
        provider.getCommitteesAtEpoch(beaconStateInternal.getSlot());

    final Committee result = future.get().get().get(0);
    assertEquals(ONE, result.slot);
    assertEquals(ZERO, result.index);
    assertEquals(List.of(1), result.committee);
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldThrowIfStoreNotAvailable() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    final SafeFuture<Optional<List<Committee>>> future = provider.getCommitteesAtEpoch(ZERO);
    verify(historicalChainData, never()).getLatestFinalizedStateAtSlot(any());
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getBeaconHead_shouldThrowIfStoreNotReady() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    assertThatThrownBy(provider::getBeaconHead).isInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getBeaconHead_shouldReturnPopulatedBeaconHead() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final Optional<BeaconHead> optionalBeaconHead = provider.getBeaconHead();
    assertThat(optionalBeaconHead.isPresent()).isTrue();

    final BeaconHead head = optionalBeaconHead.get();
    assertEquals(blockRoot, head.block_root);
    assertEquals(beaconStateInternal.hash_tree_root(), head.state_root);
    assertEquals(recentChainData.getBestSlot(), head.slot);
  }

  @Test
  public void getGenesisTime_shouldThrowIfStoreNotAvailable() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    assertThatThrownBy(provider::getGenesisTime).isInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getGenesisTime_shouldReturnValueIfStoreAvailable() {
    final UnsignedLong genesis = beaconStateInternal.getGenesis_time();
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    final UnsignedLong result = provider.getGenesisTime();
    assertEquals(genesis, result);
  }

  @Test
  public void getBlockBySlot_shouldThrowWhenStoreNotFound() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);

    final SafeFuture<Optional<GetBlockResponse>> future = provider.getBlockBySlot(ZERO);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getBlockBySlot_shouldReturnEmptyWhenSlotNotFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockInEffectAtSlot(ZERO))
        .thenReturn(completedFuture(Optional.empty()));
    final SafeFuture<Optional<GetBlockResponse>> future = provider.getBlockBySlot(ZERO);
    assertTrue(future.get().isEmpty());
  }

  @Test
  public void getBlockBySlot_shouldReturnBlockWhenFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);
    final SafeFuture<Optional<tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock>> data =
        completedFuture(Optional.of(signedBeaconBlock));

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockInEffectAtSlot(ZERO)).thenReturn(data);
    final SafeFuture<Optional<GetBlockResponse>> future = provider.getBlockBySlot(ZERO);
    verify(mockCombinedChainDataClient).getBlockInEffectAtSlot(ZERO);

    final SignedBeaconBlock result = future.get().get().signedBeaconBlock;
    assertThat(result)
        .usingRecursiveComparison()
        .isEqualTo(new SignedBeaconBlock(signedBeaconBlock));
  }

  @Test
  public void getBlockByBlockRoot_shouldThrowWhenStoreNotFound() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    final SafeFuture<Optional<GetBlockResponse>> future = provider.getBlockByBlockRoot(blockRoot);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getBlockByBlockRoot_shouldReturnEmptyWhenBlockNotFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockByBlockRoot(blockRoot))
        .thenReturn(completedFuture(Optional.empty()));
    final SafeFuture<Optional<GetBlockResponse>> future = provider.getBlockByBlockRoot(blockRoot);
    assertTrue(future.get().isEmpty());
  }

  @Test
  public void getBlockByBlockRoot_shouldReturnBlockWhenFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);
    final SafeFuture<Optional<tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock>> data =
        completedFuture(Optional.of(signedBeaconBlock));

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockByBlockRoot(blockRoot)).thenReturn(data);
    final SafeFuture<Optional<GetBlockResponse>> future = provider.getBlockByBlockRoot(blockRoot);
    verify(mockCombinedChainDataClient).getBlockByBlockRoot(blockRoot);

    final SignedBeaconBlock result = future.get().get().signedBeaconBlock;
    assertThat(result)
        .usingRecursiveComparison()
        .isEqualTo(new SignedBeaconBlock(signedBeaconBlock));
  }

  @Test
  public void getStateAtSlot_shouldThrowWhenStorageClientIsMissing() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    final SafeFuture<Optional<BeaconState>> future = provider.getStateAtSlot(ZERO);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getStateAtSlot_shouldThrowWhenStoreNotFound() {
    final RecentChainData storageClient = mock(RecentChainData.class);
    when(storageClient.isPreGenesis()).thenReturn(true);
    when(storageClient.isPreForkChoice()).thenReturn(false);
    final CombinedChainDataClient combinedChainDataClient =
        new CombinedChainDataClient(storageClient, historicalChainData);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    final SafeFuture<Optional<BeaconState>> future = provider.getStateAtSlot(ZERO);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getStateAtSlot_shouldThrowWhenPreForkChoice() {
    final RecentChainData storageClient = mock(RecentChainData.class);
    when(storageClient.isPreGenesis()).thenReturn(false);
    when(storageClient.isPreForkChoice()).thenReturn(true);
    final CombinedChainDataClient combinedChainDataClient =
        new CombinedChainDataClient(storageClient, historicalChainData);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    final SafeFuture<Optional<BeaconState>> future = provider.getStateAtSlot(ZERO);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getStateAtSlot_shouldThrowWhenHeadRootMissing() {
    final UpdatableStore store = mock(UpdatableStore.class);
    final RecentChainData storageClient = mock(RecentChainData.class);
    when(storageClient.isPreGenesis()).thenReturn(false);
    when(storageClient.isPreForkChoice()).thenReturn(true);
    when(storageClient.getStore()).thenReturn(store);
    final CombinedChainDataClient combinedChainDataClient =
        new CombinedChainDataClient(storageClient, historicalChainData);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    final SafeFuture<Optional<BeaconState>> future = provider.getStateAtSlot(ZERO);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  void getStateBySlot_shouldReturnBeaconStateWhenFound()
      throws ExecutionException, InterruptedException {
    final Bytes32 chainHead = dataStructureUtil.randomBytes32();
    final BeaconBlockAndState blockAndState =
        dataStructureUtil.randomBlockAndState(beaconStateInternal.getSlot(), beaconStateInternal);
    final SafeFuture<Optional<BeaconBlockAndState>> safeFuture =
        completedFuture(Optional.of(blockAndState));

    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isChainDataFullyAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBestBlockRoot()).thenReturn(Optional.of(chainHead));
    when(mockCombinedChainDataClient.getBlockAndStateInEffectAtSlot(ZERO)).thenReturn(safeFuture);

    final SafeFuture<Optional<BeaconState>> future = provider.getStateAtSlot(ZERO);
    verify(mockCombinedChainDataClient).getBlockAndStateInEffectAtSlot(ZERO);

    final BeaconState result = future.get().get();
    assertThat(result).usingRecursiveComparison().isEqualTo(beaconState);
  }

  @Test
  public void getStateByBlockRoot_shouldThrowWhenStoreNotFound() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    final SafeFuture<Optional<BeaconState>> future = provider.getStateByBlockRoot(blockRoot);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  void getStateByBlockRoot_shouldReturnBeaconStateWhenFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);
    final Bytes32 blockRoot = Bytes32.random();

    final SafeFuture<Optional<tech.pegasys.teku.datastructures.state.BeaconState>>
        futureBeaconState = completedFuture(Optional.of(beaconStateInternal));

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getStateByBlockRoot(blockRoot)).thenReturn(futureBeaconState);
    final SafeFuture<Optional<BeaconState>> future = provider.getStateByBlockRoot(blockRoot);
    verify(mockCombinedChainDataClient).getStateByBlockRoot(blockRoot);

    final BeaconState result = future.get().get();
    assertThat(result).usingRecursiveComparison().isEqualTo(beaconState);
  }

  @Test
  void getValidatorsByValidatorsRequest_shouldIncludeMissingValidators()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);
    final BeaconBlockAndState blockAndState =
        dataStructureUtil.randomBlockAndState(1, beaconStateInternal);
    final SafeFuture<Optional<BeaconBlockAndState>> safeFuture =
        completedFuture(Optional.of(blockAndState));
    final ValidatorsRequest smallRequest =
        new ValidatorsRequest(compute_epoch_at_slot(beaconState.slot), List.of(BLSPubKey.empty()));
    when(mockCombinedChainDataClient.isChainDataFullyAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockAndStateInEffectAtSlot(any())).thenReturn(safeFuture);

    final SafeFuture<Optional<BeaconValidators>> future =
        provider.getValidatorsByValidatorsRequest(smallRequest);
    final Optional<BeaconValidators> optionalValidators = future.get();
    final BeaconValidators validators = optionalValidators.get();

    assertThat(validators.validators.size()).isEqualTo(1);
    final ValidatorWithIndex expected = new ValidatorWithIndex(BLSPubKey.empty());
    assertThat(validators.validators.get(0)).isEqualToComparingFieldByField(expected);
  }

  @Test
  void getValidatorsByValidatorsRequest_shouldIncludeValidators()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);
    final BeaconBlockAndState blockAndState =
        dataStructureUtil.randomBlockAndState(1, beaconStateInternal);
    final tech.pegasys.teku.datastructures.state.BeaconState state = blockAndState.getState();
    final SafeFuture<Optional<BeaconBlockAndState>> safeFuture =
        completedFuture(Optional.of(blockAndState));
    final ValidatorsRequest validatorsRequest =
        new ValidatorsRequest(
            compute_epoch_at_slot(beaconState.slot),
            List.of(
                beaconState.validators.get(0).pubkey,
                beaconState.validators.get(11).pubkey,
                beaconState.validators.get(99).pubkey));
    when(mockCombinedChainDataClient.isChainDataFullyAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockAndStateInEffectAtSlot(any())).thenReturn(safeFuture);
    final SafeFuture<Optional<BeaconValidators>> future =
        provider.getValidatorsByValidatorsRequest(validatorsRequest);

    final Optional<BeaconValidators> optionalValidators = future.get();
    final BeaconValidators validators = optionalValidators.get();

    assertThat(validators.validators.size()).isEqualTo(3);
    assertThat(validators.validators.get(0))
        .usingRecursiveComparison()
        .isEqualTo(new ValidatorWithIndex(state.getValidators().get(0), state));
    assertThat(validators.validators.get(1))
        .usingRecursiveComparison()
        .isEqualTo(new ValidatorWithIndex(state.getValidators().get(11), state));
    assertThat(validators.validators.get(2))
        .usingRecursiveComparison()
        .isEqualTo(new ValidatorWithIndex(state.getValidators().get(99), state));
  }

  @Test
  public void getFork_shouldThrowIfNoBlockRoot() {
    ChainDataProvider provider =
        new ChainDataProvider(mockRecentChainData, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockRecentChainData.getBestState()).thenReturn(Optional.empty());
    assertThatThrownBy(provider::getFork).isInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getFork_shouldHaveForkIfBlockRootNotEmpty() {
    final ChainDataProvider provider =
        new ChainDataProvider(mockRecentChainData, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockRecentChainData.getBestState()).thenReturn(Optional.of(beaconStateInternal));
    final Fork result = provider.getFork();
    verify(mockCombinedChainDataClient).isStoreAvailable();
    assertThat(result).isEqualToComparingFieldByField(beaconState.fork);
  }
}
