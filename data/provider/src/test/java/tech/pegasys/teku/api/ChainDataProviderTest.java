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
import java.util.Set;
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
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.BeaconHead;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.api.schema.Committee;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SignedBeaconBlockHeader;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.api.schema.ValidatorWithIndex;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.core.stategenerator.CheckpointStateGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointState;
import tech.pegasys.teku.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
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
  public void stateParameterToSlot_shouldParseHead() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.stateParameterToSlot("head");
    assertThat(result.isPresent()).isTrue();
    assertThat(result).isEqualTo(recentChainData.getCurrentSlot());
  }

  @Test
  public void stateParameterToSlot_shouldDetectInvalidValues() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThrows(IllegalArgumentException.class, () -> provider.stateParameterToSlot("hea"));
  }

  @Test
  public void stateParameterToSlot_shouldDetectStatesInFuture() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThrows(IllegalArgumentException.class, () -> provider.stateParameterToSlot("12345678"));
  }

  @Test
  public void stateParameterToSlot_shouldParseGenesis() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.stateParameterToSlot("genesis");
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(ZERO);
  }

  @Test
  public void stateParameterToSlot_shouldParseFinalized() {
    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().get();
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.stateParameterToSlot("finalized");
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(finalizedCheckpoint.getEpochStartSlot());
  }

  @Test
  public void stateParameterToSlot_shouldParseJustified() {
    final Checkpoint justifiedCheckpoint = recentChainData.getJustifiedCheckpoint().get();
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.stateParameterToSlot("justified");
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(justifiedCheckpoint.getEpochStartSlot());
  }

  @Test
  public void stateParameterToSlot_shouldParseSlotNumber() {
    final UInt64 slot = UInt64.valueOf(1);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.stateParameterToSlot(slot.toString());
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
  public void validatorsDetails_shouldGetResponse() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    assertValidatorsRespondsWithCorrectValidatorsAtHead(provider, List.of(0, 1, 2));
  }

  @Test
  public void getValidatorsDetails_shouldReturnEmptyListWhenNoValidatorIndicesProvided() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    assertThatSafeFuture(provider.getValidatorsDetailsBySlot(ONE, emptyList()))
        .isCompletedWithValue(Optional.of(emptyList()));
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
  public void blockParameterToSlot_shouldRejectInvalidInput() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    assertThrows(IllegalArgumentException.class, () -> provider.blockParameterToSlot("headt"));
  }

  @Test
  public void blockParameterToSlot_shouldThrowWhenStoreNotFound() {
    final ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    assertThrows(ChainDataUnavailableException.class, () -> provider.blockParameterToSlot("1"));
  }

  @Test
  public void blockParameterToSlot_shouldParseBlockRoot() {
    final Bytes32 blockRoot = recentChainData.getBestBlockRoot().orElse(Bytes32.ZERO);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.blockParameterToSlot(blockRoot.toHexString());
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(recentChainData.getHeadBlock().get().getSlot());
  }

  @Test
  public void blockParameterToSlot_shouldFindHeadBlock() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.blockParameterToSlot("head");
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(recentChainData.getHeadBlock().get().getSlot());
  }

  @Test
  public void blockParameterToSlot_shouldFindGenesisBlock() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    Optional<UInt64> result = provider.blockParameterToSlot("genesis");
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(ZERO);
  }

  @Test
  public void getBlockHeaderByBlockId_shouldGetHeadBlock()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock block =
        combinedChainDataClient.getBestBlock().get();
    BlockHeader result = provider.getBlockHeader("head").get().get();
    final BeaconBlockHeader beaconBlockHeader =
        new BeaconBlockHeader(
            block.getSlot(),
            block.getMessage().getProposer_index(),
            block.getParent_root(),
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
  public void shouldGetBlockHeadersOnEmptyChainHeadSlot() {
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    final UInt64 headSlot = recentChainData.getHeadSlot();
    storageSystem.chainUpdater().advanceChain(headSlot.plus(1));

    final SafeFuture<List<BlockHeader>> future =
        provider.getBlockHeaders(Optional.empty(), Optional.empty());
    final BlockHeader header = future.join().get(0);
    assertThat(header.header.message.slot).isEqualTo(headSlot);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorIndex() {
    final DataStructureUtil data = new DataStructureUtil();
    final tech.pegasys.teku.datastructures.state.BeaconState internalState =
        data.randomBeaconState(1024);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    List<Integer> indexes =
        provider.getFilteredValidatorList(internalState, List.of("1", "33"), emptySet()).stream()
            .map(v -> v.index.intValue())
            .collect(toList());
    assertThat(indexes).containsExactly(1, 33);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorPubkey() {
    final DataStructureUtil data = new DataStructureUtil();
    final tech.pegasys.teku.datastructures.state.BeaconState internalState =
        data.randomBeaconState(1024);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);
    final String key = internalState.getValidators().get(12).getPubkey().toString();
    List<String> pubkeys =
        provider.getFilteredValidatorList(internalState, List.of(key), emptySet()).stream()
            .map(v -> v.validator.pubkey.toHexString())
            .collect(toList());
    assertThat(pubkeys).containsExactly(key);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorStatus() {
    final DataStructureUtil data = new DataStructureUtil();
    final tech.pegasys.teku.datastructures.state.BeaconState internalState =
        data.randomBeaconState(11);
    final ChainDataProvider provider =
        new ChainDataProvider(recentChainData, combinedChainDataClient);

    assertThat(
            provider.getFilteredValidatorList(
                internalState, emptyList(), Set.of(ValidatorStatus.pending_initialized)))
        .hasSize(11);
    assertThat(
            provider.getFilteredValidatorList(
                internalState, emptyList(), Set.of(ValidatorStatus.active_ongoing)))
        .hasSize(0);
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
