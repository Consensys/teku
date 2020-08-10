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
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorWithIndex;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.core.stategenerator.CheckpointStateGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.config.StateStorageMode;

public class ChainDataProviderTest {
  private final StorageSystem storageSystem =
      InMemoryStorageSystem.createEmptyLatestStorageSystem(StateStorageMode.ARCHIVE);
  private CombinedChainDataClient combinedChainDataClient;
  private StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private tech.pegasys.teku.datastructures.state.BeaconState beaconStateInternal;

  private SignedBlockAndState bestBlock;
  private tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock signedBeaconBlock;
  private BeaconState beaconState;
  private Bytes32 blockRoot;
  private Bytes32 stateRoot;
  private UInt64 slot;
  private RecentChainData recentChainData;
  private CombinedChainDataClient mockCombinedChainDataClient = mock(CombinedChainDataClient.class);
  private RecentChainData mockRecentChainData = mock(RecentChainData.class);

  @BeforeEach
  public void setup() {
    slot = UInt64.valueOf(SLOTS_PER_EPOCH * 3);
    storageSystem.chainUpdater().initializeGenesis();
    bestBlock = storageSystem.chainUpdater().advanceChain(slot);
    storageSystem.chainUpdater().updateBestBlock(bestBlock);

    recentChainData = storageSystem.recentChainData();
    beaconStateInternal = bestBlock.getState();

    signedBeaconBlock = bestBlock.getBlock();
    beaconState = new BeaconState(beaconStateInternal);
    combinedChainDataClient = storageSystem.combinedChainDataClient();
    blockRoot = bestBlock.getRoot();
    stateRoot = beaconStateInternal.hash_tree_root();
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnEmptyListWhenStateAtSlotIsNotFound()
      throws Exception {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final SafeFuture<Optional<List<Committee>>> future =
        provider.getCommitteesAtEpoch(UInt64.valueOf(50));
    assertThat(future.get()).isEmpty();
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnEmptyListWhenAFutureEpochIsRequested()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final UInt64 futureEpoch = slot.plus(UInt64.valueOf(SLOTS_PER_EPOCH));

    final SafeFuture<Optional<List<Committee>>> future = provider.getCommitteesAtEpoch(futureEpoch);
    assertThat(future.get()).isEmpty();
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnAListOfCommittees()
      throws ExecutionException, InterruptedException {
    final List<CommitteeAssignment> committeeAssignments =
        List.of(new CommitteeAssignment(List.of(1), ZERO, ONE));
    final UInt64 currentEpoch = compute_epoch_at_slot(bestBlock.getSlot());

    // Setup data
    final UInt64 queryEpoch = currentEpoch.equals(ZERO) ? currentEpoch : currentEpoch.minus(ONE);
    final Checkpoint checkpoint =
        storageSystem.chainBuilder().getCurrentCheckpointForEpoch(queryEpoch);
    final SignedBlockAndState checkpointBlockAndState =
        storageSystem.chainBuilder().getBlockAndState(checkpoint.getRoot()).orElseThrow();
    final CheckpointState checkpointState =
        CheckpointStateGenerator.generate(checkpoint, checkpointBlockAndState);

    final ChainDataProvider provider =
        new ChainDataProvider(mockRecentChainData, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isChainDataFullyAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBestBlockRoot())
        .thenReturn(Optional.of(bestBlock.getRoot()));
    when(mockCombinedChainDataClient.getCommitteesFromState(any(), any()))
        .thenReturn(committeeAssignments);
    when(mockRecentChainData.getBestSlot()).thenReturn(bestBlock.getSlot());
    when(mockCombinedChainDataClient.getCheckpointStateAtEpoch(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(checkpointState)));
    final SafeFuture<Optional<List<Committee>>> future =
        provider.getCommitteesAtEpoch(currentEpoch);

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
    final UInt64 genesis = beaconStateInternal.getGenesis_time();
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    final UInt64 result = provider.getGenesisTime();
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
  public void getStateByBlockRoot_shouldThrowWhenStoreNotFound() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    final SafeFuture<Optional<BeaconState>> future = provider.getStateByBlockRoot(blockRoot);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getStateByStateRoot_shouldThrowWhenStoreNotFound() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    final SafeFuture<Optional<BeaconState>> future = provider.getStateByStateRoot(blockRoot);
    assertThatThrownBy(future::get).hasCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getStateByStateRoot_shouldReturnEmptyWhenNotFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, mockCombinedChainDataClient);
    final SafeFuture<Optional<tech.pegasys.teku.datastructures.state.BeaconState>>
        futureBeaconState = completedFuture(Optional.of(beaconStateInternal));
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getStateByStateRoot(stateRoot)).thenReturn(futureBeaconState);
    final SafeFuture<Optional<BeaconState>> future = provider.getStateByStateRoot(stateRoot);
    verify(mockCombinedChainDataClient).getStateByStateRoot(stateRoot);

    final BeaconState result = future.get().get();
    assertThat(result).usingRecursiveComparison().isEqualTo(beaconState);
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
    final SafeFuture<Optional<BeaconBlockAndState>> safeFuture =
        completedFuture(Optional.of(bestBlock.toUnsigned()));
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
    final tech.pegasys.teku.datastructures.state.BeaconState state = bestBlock.getState();
    final SafeFuture<Optional<BeaconBlockAndState>> safeFuture =
        completedFuture(Optional.of(bestBlock.toUnsigned()));
    final ValidatorsRequest validatorsRequest =
        new ValidatorsRequest(
            compute_epoch_at_slot(beaconState.slot),
            List.of(beaconState.validators.get(0).pubkey, beaconState.validators.get(2).pubkey));
    when(mockCombinedChainDataClient.isChainDataFullyAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockAndStateInEffectAtSlot(any())).thenReturn(safeFuture);
    final SafeFuture<Optional<BeaconValidators>> future =
        provider.getValidatorsByValidatorsRequest(validatorsRequest);

    final Optional<BeaconValidators> optionalValidators = future.get();
    final BeaconValidators validators = optionalValidators.get();

    assertThat(validators.validators.size()).isEqualTo(2);
    assertThat(validators.validators.get(0))
        .usingRecursiveComparison()
        .isEqualTo(new ValidatorWithIndex(state.getValidators().get(0), state));
    assertThat(validators.validators.get(1))
        .usingRecursiveComparison()
        .isEqualTo(new ValidatorWithIndex(state.getValidators().get(2), state));
  }

  @Test
  public void getForkInfo_shouldThrowIfNoBlockRoot() {
    ChainDataProvider provider =
        new ChainDataProvider(mockRecentChainData, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockRecentChainData.getBestState()).thenReturn(Optional.empty());
    assertThatThrownBy(provider::getForkInfo).isInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getForkInfo_shouldHaveForkIfBlockRootNotEmpty() {
    final ChainDataProvider provider =
        new ChainDataProvider(mockRecentChainData, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockRecentChainData.getBestState()).thenReturn(Optional.of(beaconStateInternal));
    final GetForkResponse result = provider.getForkInfo();
    verify(mockCombinedChainDataClient).isStoreAvailable();

    assertThat(result.previous_version).isEqualTo(beaconState.fork.previous_version);
    assertThat(result.current_version).isEqualTo(beaconState.fork.current_version);
    assertThat(result.epoch).isEqualTo(beaconState.fork.epoch);
    assertThat(result.genesis_validators_root).isEqualTo(beaconState.genesis_validators_root);
  }
}
