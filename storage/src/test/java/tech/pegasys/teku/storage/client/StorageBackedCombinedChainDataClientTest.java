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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

public class StorageBackedCombinedChainDataClientTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StorageSystem storageSystem =
      InMemoryStorageSystem.createEmptyLatestStorageSystem(StateStorageMode.ARCHIVE);
  private CombinedChainDataClient combinedChainDataClient;
  private StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private BeaconState beaconState;

  private SignedBlockAndState bestBlock;
  private UnsignedLong slot;

  @BeforeEach
  public void setup() {
    slot = UnsignedLong.valueOf(SLOTS_PER_EPOCH * 3);
    storageSystem.chainUpdater().initializeGenesis();
    bestBlock = storageSystem.chainUpdater().advanceChain(slot);
    storageSystem.chainUpdater().updateBestBlock(bestBlock);
    beaconState = bestBlock.getState();

    combinedChainDataClient = storageSystem.combinedChainDataClient();
  }

  @Test
  public void getStateByStateRoot_shouldReturnEmptyIfNotReady()
      throws ExecutionException, InterruptedException {
    RecentChainData recentChainData = mock(RecentChainData.class);
    when(recentChainData.getStore()).thenReturn(null);
    CombinedChainDataClient client =
        new CombinedChainDataClient(recentChainData, historicalChainData);
    SafeFuture<Optional<BeaconState>> future =
        client.getStateByStateRoot(beaconState.hash_tree_root());

    assertThat(future.get()).isEqualTo(Optional.empty());
  }

  @Test
  public void getStateByStateRoot_shouldReturnEmptyIfNotFound()
      throws ExecutionException, InterruptedException {
    Optional<BeaconState> result =
        combinedChainDataClient.getStateByStateRoot(dataStructureUtil.randomBytes32()).get();
    assertThat(result).isEqualTo(Optional.empty());
  }
}
