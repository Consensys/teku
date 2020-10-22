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
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.GetBlockResponse;
import tech.pegasys.teku.api.response.GetForkResponse;
import tech.pegasys.teku.api.response.v1.beacon.BlockHeader;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.api.schema.ValidatorWithIndex;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.stategenerator.CheckpointStateGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.StateStorageMode;

public class ChainDataProviderTest {
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private CombinedChainDataClient combinedChainDataClient;
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private tech.pegasys.teku.datastructures.state.BeaconState beaconStateInternal;

  private SignedBlockAndState bestBlock;
  private tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock signedBeaconBlock;
  private BeaconState beaconState;
  private Bytes32 blockRoot;
  private Bytes32 stateRoot;
  private UInt64 slot;
  private RecentChainData recentChainData;
  private final CombinedChainDataClient mockCombinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData mockRecentChainData = mock(RecentChainData.class);
  private UInt64 actualBalance;

  @BeforeEach
  public void setup() {
    slot = UInt64.valueOf(SLOTS_PER_EPOCH * 3);
    actualBalance = UInt64.valueOf(Constants.MAX_EFFECTIVE_BALANCE + 100000);
    storageSystem.chainUpdater().initializeGenesis(true, actualBalance);
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
    final UInt64 futureEpoch = slot.plus(SLOTS_PER_EPOCH);

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
    when(mockRecentChainData.getHeadSlot()).thenReturn(bestBlock.getSlot());
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
    assertEquals(recentChainData.getHeadSlot(), head.slot);
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
  public void validatorParameterToIndex_shouldThrowWhenStoreNotFound() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    assertThrows(
        ChainDataUnavailableException.class, () -> provider.validatorParameterToIndex("1"));
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

  @Test
  public void parameterToSlotshouldParseHead() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.parameterToSlot("head");
    assertThat(result.isPresent()).isTrue();
    assertThat(result).isEqualTo(recentChainData.getCurrentSlot());
  }

  @Test
  public void parameterToSlotshouldDetectInvalidValues() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThrows(IllegalArgumentException.class, () -> provider.parameterToSlot("hea"));
  }

  @Test
  public void parameterToSlotshouldDetectStatesInFuture() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThrows(IllegalArgumentException.class, () -> provider.parameterToSlot("12345678"));
  }

  @Test
  public void parameterToSlotshouldParseGenesis() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.parameterToSlot("genesis");
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(ZERO);
  }

  @Test
  public void parameterToSlotshouldParseFinalized() {
    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().get();
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.parameterToSlot("finalized");
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(finalizedCheckpoint.getEpochStartSlot());
  }

  @Test
  public void parameterToSlotshouldParseJustified() {
    final Checkpoint justifiedCheckpoint = recentChainData.getJustifiedCheckpoint().get();
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.parameterToSlot("justified");
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(justifiedCheckpoint.getEpochStartSlot());
  }

  @Test
  public void parameterToSlotshouldParseSlotNumber() {
    final UInt64 slot = UInt64.valueOf(1);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.parameterToSlot(slot.toString());
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(slot);
  }

  @Test
  public void validatorParameterToIndex_shouldAcceptValidatorRoot() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Validator validator =
        new Validator(recentChainData.getBestState().get().getValidators().get(1));

    assertThat(provider.validatorParameterToIndex(validator.pubkey.toHexString()))
        .isEqualTo(Optional.of(1));
  }

  @Test
  public void validatorParameterToIndex_shouldAcceptValidatorId() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThat(provider.validatorParameterToIndex("2")).isEqualTo(Optional.of(2));
  }

  @Test
  public void validatorParameterToIndex_shouldThrowException() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThrows(IllegalArgumentException.class, () -> provider.validatorParameterToIndex("2a"));
  }

  @Test
  public void validatorParameterToIndex_shouldDetectIndexOutOfBounds() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThrows(
        IllegalArgumentException.class, () -> provider.validatorParameterToIndex("1234567"));
  }

  @Test
  public void validatorParameterToIndex_shouldDetectAboveMaxInt() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            provider.validatorParameterToIndex(
                UInt64.valueOf(Integer.MAX_VALUE).increment().toString()));
  }

  @Test
  public void validatorParameterToIndex_shouldThrowExceptionWithInvalidPublicKey() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThrows(
        IllegalArgumentException.class,
        () -> provider.validatorParameterToIndex(Bytes32.EMPTY.toHexString()));
  }

  @Test
  public void validatorDetails_shouldReturnEmptyForFutureState() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final SafeFuture<Optional<ValidatorResponse>> response =
        provider.getValidatorDetailsBySlot(UInt64.valueOf(12345678), Optional.of(1));
    assertThatSafeFuture(response).isCompletedWithEmptyOptional();
  }

  @Test
  public void getValidatorDetails_shouldReturnEmptyFromEmptyValidatorIndex() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    assertThatSafeFuture(provider.getValidatorDetailsBySlot(ONE, Optional.empty()))
        .isCompletedWithEmptyOptional();
  }

  @Test
  public void validatorDetails_shouldGetResponse() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    Validator validator =
        new Validator(recentChainData.getBestState().get().getValidators().get(1));
    assertValidatorRespondsWithCorrectValidatorAtHead(provider, validator, 1);
  }

  @Test
  public void validatorsDetails_shouldReturnEmptyForFutureState() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    SafeFuture<Optional<List<ValidatorResponse>>> response =
        provider.getValidatorsDetailsBySlot(UInt64.valueOf(12345678), List.of(1));
    assertThatSafeFuture(response).isCompletedWithEmptyOptional();
  }

  @Test
  public void getValidatorsDetails_shouldReturnEmptyListWhenNoValidatorIndicesProvided() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    assertThatSafeFuture(provider.getValidatorsDetailsBySlot(ONE, emptyList()))
        .isCompletedWithValue(Optional.of(emptyList()));
  }

  @Test
  public void validatorsDetails_shouldGetResponse() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    assertValidatorsRespondsWithCorrectValidatorsAtHead(provider, List.of(0, 1, 2));
  }

  @Test
  public void getForkAtSlot_shouldGetCurrentFork() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final Bytes4 FORK_ONE = Bytes4.fromHexString("0x00000001");
    final Optional<Fork> expectedResult = Optional.of(new Fork(FORK_ONE, FORK_ONE, ZERO));
    assertThat(provider.getForkAtSlot(ONE).join()).isEqualTo(expectedResult);
  }

  @Test
  public void parameterToSlot_shouldParseBlockRoot() {
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElse(Bytes32.ZERO);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.parameterToSlot(blockRoot.toHexString());
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(recentChainData.getHeadBlock().get().getSlot());
  }

  @Test
  public void getBlockHeaders_shouldGetHeadBlockIfNoParameters()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock block =
        combinedChainDataClient.getBestBlock().get();
    List<BlockHeader> results = provider.getBlockHeaders(Optional.empty(), Optional.empty()).get();
    assertThat(results.get(0).root).isEqualTo(block.getRoot());
  }

  @Test
  public void getBlockHeaders_shouldGetBlockGivenSlot()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final UInt64 slot = combinedChainDataClient.getCurrentSlot();
    List<BlockHeader> results = provider.getBlockHeaders(Optional.empty(), Optional.of(slot)).get();
    assertThat(results.get(0).header.message.slot).isEqualTo(slot);
  }

  @Test
  public void shouldGetBlockHeaderByBlockRoot_ForCanonicalBlock()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final SignedBeaconBlock block =
        new SignedBeaconBlock(combinedChainDataClient.getBestBlock().get());
    Optional<BlockHeader> results =
        provider.getBlockHeaderByRoot(block.asInternalSignedBeaconBlock().getRoot()).get();
    assertThat(results.get().header.message.slot).isEqualTo(slot);
    assertThat(results.get().canonical).isEqualTo(true);
  }

  @Test
  public void shouldGetBlockHeaderByBlockRoot_ForNonCanonicalBlock()
      throws ExecutionException, InterruptedException {
    ChainBuilder forkChainBuilder = storageSystem.chainBuilder().fork();
    ChainUpdater forkChainUpdater =
        new ChainUpdater(storageSystem.recentChainData(), forkChainBuilder);

    SignedBlockAndState forkBlock = forkChainUpdater.advanceChain(slot.plus(10));
    storageSystem.chainUpdater().advanceChain(slot.plus(14));

    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final SignedBeaconBlock block =
        new SignedBeaconBlock(combinedChainDataClient.getBestBlock().get());
    Optional<BlockHeader> results =
        provider.getBlockHeaderByRoot(forkBlock.getBlock().getRoot()).get();
    assertThat(results.get().header.message.slot).isEqualTo(forkBlock.getSlot());
    assertThat(results.get().canonical).isEqualTo(false);
  }

  private void assertValidatorRespondsWithCorrectValidatorAtHead(
      final ChainDataProvider provider, final Validator validator, final Integer validatorId) {
    SafeFuture<Optional<ValidatorResponse>> response =
        provider.getValidatorDetailsBySlot(ZERO, Optional.of(validatorId));
    Optional<ValidatorResponse> maybeValidator = response.join();
    assertThat(maybeValidator.isPresent()).isTrue();
    assertThat(maybeValidator.get())
        .isEqualTo(
            new ValidatorResponse(
                ONE,
                actualBalance,
                ValidatorStatus.active_ongoing,
                new Validator(
                    validator.pubkey,
                    validator.withdrawal_credentials,
                    UInt64.valueOf("32000000000"),
                    false,
                    ZERO,
                    ZERO,
                    FAR_FUTURE_EPOCH,
                    FAR_FUTURE_EPOCH)));
  }

  private void assertValidatorsRespondsWithCorrectValidatorsAtHead(
      final ChainDataProvider provider, final List<Integer> validatorIds) {
    final List<ValidatorResponse> expectedValidators =
        validatorIds.stream()
            .map(id -> ValidatorResponse.fromState(beaconStateInternal, id))
            .collect(toList());
    SafeFuture<Optional<List<ValidatorResponse>>> response =
        provider.getValidatorsDetailsBySlot(slot, validatorIds);
    assertThatSafeFuture(response).isCompletedWithValue(Optional.of(expectedValidators));
  }
}
