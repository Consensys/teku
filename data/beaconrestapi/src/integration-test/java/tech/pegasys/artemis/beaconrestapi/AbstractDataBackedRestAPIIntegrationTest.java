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

package tech.pegasys.artemis.beaconrestapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.statetransition.forkchoice.ForkChoice;
import tech.pegasys.artemis.storage.api.StorageUpdateChannel;
import tech.pegasys.artemis.storage.api.StubStorageQueryChannel;
import tech.pegasys.artemis.storage.client.CombinedChainDataClient;
import tech.pegasys.artemis.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.artemis.storage.events.SuccessfulStorageUpdateResult;
import tech.pegasys.artemis.util.async.SafeFuture;

public abstract class AbstractDataBackedRestAPIIntegrationTest
    extends AbstractBeaconRestAPIIntegrationTest {

  protected static final UnsignedLong SIX = UnsignedLong.valueOf(6);
  protected static final UnsignedLong SEVEN = UnsignedLong.valueOf(7);
  protected static final UnsignedLong EIGHT = UnsignedLong.valueOf(8);
  protected static final UnsignedLong NINE = UnsignedLong.valueOf(9);
  protected static final UnsignedLong TEN = UnsignedLong.valueOf(10);
  protected BeaconChainUtil beaconChainUtil;
  protected final JsonProvider jsonProvider = new JsonProvider();
  private BlockImporter blockImporter;

  @Override
  @BeforeEach
  public void setup() {
    final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
    final ForkChoice fockChoice = mock(ForkChoice.class);
    when(storageUpdateChannel.onStorageUpdate(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new SuccessfulStorageUpdateResult(Collections.emptySet(), Collections.emptySet())));
    final EventBus eventBus = new EventBus();
    recentChainData = MemoryOnlyRecentChainData.create(eventBus);
    beaconChainUtil = BeaconChainUtil.create(16, recentChainData);
    beaconChainUtil.initializeStorage();
    historicalChainData = new StubStorageQueryChannel();
    blockImporter = new BlockImporter(recentChainData, fockChoice, eventBus);
    combinedChainDataClient = new CombinedChainDataClient(recentChainData, historicalChainData);
    dataProvider =
        new DataProvider(
            recentChainData,
            combinedChainDataClient,
            p2PNetwork,
            syncService,
            validatorApiChannel,
            blockImporter);
    beaconRestApi = new BeaconRestApi(dataProvider, config);
    beaconRestApi.start();
    client = new OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build();
  }

  public List<SignedBeaconBlock> withBlockDataAtSlot(UnsignedLong... slots) throws Exception {
    final ArrayList<SignedBeaconBlock> results = new ArrayList<>();
    for (UnsignedLong slot : slots) {
      results.add(new SignedBeaconBlock(beaconChainUtil.createAndImportBlockAtSlot(slot)));
    }
    return results;
  }

  public void withNoBlockDataAtSlots(UnsignedLong slot) {
    beaconChainUtil.setSlot(slot);
  }

  public void withFinalizedChainAtEpoch(UnsignedLong epoch) throws Exception {
    beaconChainUtil.finalizeChainAtEpoch(epoch);
  }
}
