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

package tech.pegasys.artemis.api;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.schema.BeaconHead;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.api.schema.Committee;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.util.async.SafeFuture;

public class ChainDataProviderTest {
  private static CombinedChainDataClient combinedChainDataClient;
  private static HistoricalChainData historicalChainData = mock(HistoricalChainData.class);
  private static tech.pegasys.artemis.datastructures.state.BeaconState beaconStateInternal;
  private static BeaconState beaconState;
  private static Bytes32 blockRoot;
  private static UnsignedLong slot;
  private static EventBus localEventBus;
  private static ChainStorageClient chainStorageClient;
  private final tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock signedBeaconBlock =
      DataStructureUtil.randomSignedBeaconBlock(1, 1);
  private CombinedChainDataClient mockCombinedChainDataClient = mock(CombinedChainDataClient.class);
  private ChainStorageClient mockChainStorageClient = mock(ChainStorageClient.class);

  @BeforeAll
  public static void setup() {
    localEventBus = new EventBus();
    chainStorageClient = ChainStorageClient.memoryOnlyClient(localEventBus);
    beaconStateInternal = DataStructureUtil.randomBeaconState(11233);
    beaconState = new BeaconState(beaconStateInternal);
    chainStorageClient.initializeFromGenesis(beaconStateInternal);
    combinedChainDataClient = new CombinedChainDataClient(chainStorageClient, historicalChainData);
    blockRoot = chainStorageClient.getBestBlockRoot();
    slot = chainStorageClient.getBlockState(blockRoot).get().getSlot();
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnEmptyListWhenStateAtSlotIsNotFound()
      throws Exception {
    ChainDataProvider provider = new ChainDataProvider(null, combinedChainDataClient);

    when(historicalChainData.getFinalizedStateAtSlot(ZERO))
        .thenReturn(completedFuture(Optional.empty()));
    SafeFuture<List<Committee>> future = provider.getCommitteesAtEpoch(ZERO);

    verify(historicalChainData).getFinalizedStateAtSlot(ZERO);
    assertEquals(future.get(), List.of());
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnEmptyListWhenAFutureEpochIsRequested()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider = new ChainDataProvider(chainStorageClient, combinedChainDataClient);
    UnsignedLong futureEpoch = slot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH));

    SafeFuture<List<Committee>> future = provider.getCommitteesAtEpoch(futureEpoch);
    assertEquals(future.get(), List.of());
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnAListOfCommittees()
      throws ExecutionException, InterruptedException {
    List<CommitteeAssignment> committeeAssignments =
        List.of(new CommitteeAssignment(List.of(1), ZERO, ONE));
    ChainDataProvider provider =
        new ChainDataProvider(mockChainStorageClient, mockCombinedChainDataClient);

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getCommitteeAssignmentAtEpoch(beaconStateInternal.getSlot()))
        .thenReturn(completedFuture(committeeAssignments));
    SafeFuture<List<Committee>> future =
        provider.getCommitteesAtEpoch(beaconStateInternal.getSlot());

    verify(mockCombinedChainDataClient).isStoreAvailable();
    verify(mockCombinedChainDataClient)
        .getCommitteeAssignmentAtEpoch(beaconStateInternal.getSlot());
    Committee result = future.get().get(0);
    assertEquals(ONE, result.slot);
    assertEquals(ZERO, result.index);
    assertEquals(List.of(1), result.committee);
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnEmptyListIfStoreNotAvailable()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    SafeFuture<List<Committee>> future = provider.getCommitteesAtEpoch(ZERO);
    verify(historicalChainData, never()).getFinalizedStateAtSlot(any());
    assertEquals(future.get(), List.of());
  }

  @Test
  public void getBeaconHead_shouldReturnEmptyIfStoreNotReady() {
    ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    Optional<BeaconHead> data = provider.getBeaconHead();
    assertTrue(data.isEmpty());
  }

  @Test
  public void getBeaconHead_shouldReturnPopulatedBeaconHead() {
    ChainDataProvider provider = new ChainDataProvider(chainStorageClient, combinedChainDataClient);

    Optional<BeaconHead> optionalBeaconHead = provider.getBeaconHead();

    assertTrue(optionalBeaconHead.isPresent());
    BeaconHead head = optionalBeaconHead.get();
    assertEquals(blockRoot, head.block_root);
    assertEquals(beaconStateInternal.hash_tree_root(), head.state_root);
    assertEquals(chainStorageClient.getBestSlot(), head.slot);
  }

  @Test
  public void getBeaconHead_shouldReturnEmptyIfHeadNotFound() {
    ChainDataProvider provider =
        new ChainDataProvider(mockChainStorageClient, combinedChainDataClient);

    when(mockChainStorageClient.getBestBlockRoot()).thenReturn(null);

    Optional<BeaconHead> data = provider.getBeaconHead();
    assertTrue(data.isEmpty());
  }

  @Test
  public void getGenesisTime_shouldReturnEmptyIfStoreNotAvailable() {
    ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);

    Optional<UnsignedLong> optionalData = provider.getGenesisTime();
    assertTrue(optionalData.isEmpty());
  }

  @Test
  public void getGenesisTime_shouldReturnValueIfStoreAvailable() {
    UnsignedLong genesis = beaconStateInternal.getGenesis_time();
    ChainDataProvider provider = new ChainDataProvider(chainStorageClient, combinedChainDataClient);

    Optional<UnsignedLong> optionalData = provider.getGenesisTime();
    assertEquals(genesis, optionalData.get());
  }

  @Test
  public void getBlockBySlot_shouldReturnEmptyWhenStoreNotFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);

    SafeFuture<Optional<SignedBeaconBlock>> future = provider.getBlockBySlot(ZERO);
    assertTrue(future.get().isEmpty());
  }

  @Test
  public void getBlockBySlot_shouldReturnEmptyWhenSlotNotFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider =
        new ChainDataProvider(chainStorageClient, mockCombinedChainDataClient);

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockBySlot(ZERO))
        .thenReturn(completedFuture(Optional.empty()));
    SafeFuture<Optional<SignedBeaconBlock>> future = provider.getBlockBySlot(ZERO);
    assertTrue(future.get().isEmpty());
  }

  @Test
  public void getBlockBySlot_shouldReturnBlockWhenFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider =
        new ChainDataProvider(chainStorageClient, mockCombinedChainDataClient);
    SafeFuture<Optional<tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock>> data =
        completedFuture(Optional.of(signedBeaconBlock));

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockBySlot(ZERO)).thenReturn(data);
    SafeFuture<Optional<SignedBeaconBlock>> future = provider.getBlockBySlot(ZERO);
    verify(mockCombinedChainDataClient).getBlockBySlot(ZERO);

    SignedBeaconBlock result = future.get().get();
    assertThat(result)
        .usingRecursiveComparison()
        .isEqualTo(new SignedBeaconBlock(signedBeaconBlock));
  }

  @Test
  public void getBlockByBlockRoot_shouldReturnEmptyWhenStoreNotFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);

    SafeFuture<Optional<SignedBeaconBlock>> future = provider.getBlockByBlockRoot(blockRoot);
    assertTrue(future.get().isEmpty());
  }

  @Test
  public void getBlockByBlockRoot_shouldReturnEmptyWhenBlockNotFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider =
        new ChainDataProvider(chainStorageClient, mockCombinedChainDataClient);

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockByBlockRoot(blockRoot))
        .thenReturn(completedFuture(Optional.empty()));
    SafeFuture<Optional<SignedBeaconBlock>> future = provider.getBlockByBlockRoot(blockRoot);
    assertTrue(future.get().isEmpty());
  }

  @Test
  public void getBlockByBlockRoot_shouldReturnBlockWhenFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider =
        new ChainDataProvider(chainStorageClient, mockCombinedChainDataClient);
    SafeFuture<Optional<tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock>> data =
        completedFuture(Optional.of(signedBeaconBlock));

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBlockByBlockRoot(blockRoot)).thenReturn(data);
    SafeFuture<Optional<SignedBeaconBlock>> future = provider.getBlockByBlockRoot(blockRoot);
    verify(mockCombinedChainDataClient).getBlockByBlockRoot(blockRoot);

    SignedBeaconBlock result = future.get().get();
    assertThat(result)
        .usingRecursiveComparison()
        .isEqualTo(new SignedBeaconBlock(signedBeaconBlock));
  }

  @Test
  public void getStateAtSlot_shouldReturnEmptyWhenStoreNotFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);

    SafeFuture<Optional<BeaconState>> future = provider.getStateAtSlot(ZERO);
    assertTrue(future.get().isEmpty());
  }

  @Test
  void getStateBySlot_shouldReturnBeaconStateWhenFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider =
        new ChainDataProvider(chainStorageClient, mockCombinedChainDataClient);
    Bytes32 blockRoot = Bytes32.random();

    SafeFuture<Optional<tech.pegasys.artemis.datastructures.state.BeaconState>> futureBeaconState =
        completedFuture(Optional.of(beaconStateInternal));

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));
    when(mockCombinedChainDataClient.getStateAtSlot(ZERO, blockRoot)).thenReturn(futureBeaconState);
    SafeFuture<Optional<BeaconState>> future = provider.getStateAtSlot(ZERO);
    verify(mockCombinedChainDataClient).getStateAtSlot(ZERO, blockRoot);

    BeaconState result = future.get().get();
    assertThat(result).usingRecursiveComparison().isEqualTo(beaconState);
  }

  @Test
  public void getStateByBlockRoot_shouldReturnEmptyWhenStoreNotFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider = new ChainDataProvider(null, mockCombinedChainDataClient);

    SafeFuture<Optional<BeaconState>> future = provider.getStateByBlockRoot(blockRoot);
    assertTrue(future.get().isEmpty());
  }

  @Test
  void getStateByBlockRoot_shouldReturnBeaconStateWhenFound()
      throws ExecutionException, InterruptedException {
    ChainDataProvider provider =
        new ChainDataProvider(chainStorageClient, mockCombinedChainDataClient);
    Bytes32 blockRoot = Bytes32.random();

    SafeFuture<Optional<tech.pegasys.artemis.datastructures.state.BeaconState>> futureBeaconState =
        completedFuture(Optional.of(beaconStateInternal));

    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(mockCombinedChainDataClient.getStateByBlockRoot(blockRoot)).thenReturn(futureBeaconState);
    SafeFuture<Optional<BeaconState>> future = provider.getStateByBlockRoot(blockRoot);
    verify(mockCombinedChainDataClient).getStateByBlockRoot(blockRoot);

    BeaconState result = future.get().get();
    assertThat(result).usingRecursiveComparison().isEqualTo(beaconState);
  }
}
