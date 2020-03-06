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
import tech.pegasys.artemis.api.schema.Committee;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.util.async.SafeFuture;

public class ChainDataProviderTest {
  // FIXME add BeaconCommitteesHandler detailed tests here so that interface tests stay simple
  private static CombinedChainDataClient combinedChainDataClient;
  private static HistoricalChainData historicalChainData = mock(HistoricalChainData.class);
  private static BeaconState beaconState;
  private static Bytes32 blockRoot;
  private static UnsignedLong slot;
  private static EventBus localEventBus;
  private static ChainStorageClient chainStorageClient;

  @SuppressWarnings("unused")
  private static UnsignedLong epoch;

  @BeforeAll
  public static void setup() {
    localEventBus = new EventBus();
    chainStorageClient = ChainStorageClient.memoryOnlyClient(localEventBus);
    beaconState = DataStructureUtil.randomBeaconState(11233);
    chainStorageClient.initializeFromGenesis(beaconState);
    combinedChainDataClient = new CombinedChainDataClient(chainStorageClient, historicalChainData);
    blockRoot = chainStorageClient.getBestBlockRoot();
    slot = chainStorageClient.getBlockState(blockRoot).get().getSlot();
    epoch = slot.dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
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
    CombinedChainDataClient myCombinedClient = mock(CombinedChainDataClient.class);
    ChainStorageClient myClient = mock(ChainStorageClient.class);
    List<CommitteeAssignment> committeeAssignments =
        List.of(new CommitteeAssignment(List.of(1), ZERO, ONE));
    ChainDataProvider provider = new ChainDataProvider(myClient, myCombinedClient);

    when(myCombinedClient.isStoreAvailable()).thenReturn(true);
    when(myCombinedClient.getCommitteeAssignmentAtEpoch(beaconState.getSlot()))
        .thenReturn(completedFuture(committeeAssignments));
    SafeFuture<List<Committee>> future = provider.getCommitteesAtEpoch(beaconState.getSlot());

    verify(myCombinedClient).isStoreAvailable();
    verify(myCombinedClient).getCommitteeAssignmentAtEpoch(beaconState.getSlot());
    Committee result = future.get().get(0);
    assertEquals(ONE, result.slot);
    assertEquals(ZERO, result.index);
    assertEquals(List.of(1), result.committee);
  }

  @Test
  public void getCommitteeAssignmentAtEpoch_shouldReturnEmptyListIfStoreNotAvailable()
      throws ExecutionException, InterruptedException {
    CombinedChainDataClient myClient = mock(CombinedChainDataClient.class);
    ChainDataProvider provider = new ChainDataProvider(null, myClient);
    when(myClient.isStoreAvailable()).thenReturn(false);
    SafeFuture<List<Committee>> future = provider.getCommitteesAtEpoch(ZERO);
    verify(historicalChainData, never()).getFinalizedStateAtSlot(any());
    assertEquals(future.get(), List.of());
  }

  @Test
  public void getBeaconHead_shouldReturnEmptyIfStoreNotReady() {
    CombinedChainDataClient myClient = mock(CombinedChainDataClient.class);
    ChainDataProvider provider = new ChainDataProvider(null, myClient);
    when(myClient.isStoreAvailable()).thenReturn(false);
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
    assertEquals(beaconState.hash_tree_root(), head.state_root);
    assertEquals(chainStorageClient.getBestSlot(), head.slot);
  }

  @Test
  public void getBeaconHead_shouldReturnEmptyIfHeadNotFound() {
    ChainStorageClient myClient = mock(ChainStorageClient.class);
    ChainDataProvider provider = new ChainDataProvider(myClient, combinedChainDataClient);

    when(myClient.getBestBlockRoot()).thenReturn(null);

    Optional<BeaconHead> data = provider.getBeaconHead();
    assertTrue(data.isEmpty());
  }

  @Test
  public void getGenesisTime_shouldReturnEmptyIfStoreNotAvailable() {
    CombinedChainDataClient myClient = mock(CombinedChainDataClient.class);
    ChainDataProvider provider = new ChainDataProvider(null, myClient);
    when(myClient.isStoreAvailable()).thenReturn(false);

    Optional<UnsignedLong> optionalData = provider.getGenesisTime();
    assertTrue(optionalData.isEmpty());
  }

  @Test
  public void getGenesisTime_shouldReturnValueIfStoreAvailable() {
    UnsignedLong genesis = beaconState.getGenesis_time();
    ChainDataProvider provider = new ChainDataProvider(chainStorageClient, combinedChainDataClient);

    Optional<UnsignedLong> optionalData = provider.getGenesisTime();
    assertEquals(genesis, optionalData.get());
  }
}
