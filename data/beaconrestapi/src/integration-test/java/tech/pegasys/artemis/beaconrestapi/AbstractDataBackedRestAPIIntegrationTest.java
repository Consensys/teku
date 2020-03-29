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

import static org.mockito.Mockito.mock;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.artemis.api.DataProvider;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.storage.HistoricalChainData;
import tech.pegasys.artemis.storage.api.StorageUpdateChannel;

public abstract class AbstractDataBackedRestAPIIntegrationTest
    extends AbstractBeaconRestAPIIntegrationTest {
  private BeaconChainUtil beaconChainUtil;

  @Override
  @BeforeEach
  public void setup() {
    final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
    final EventBus eventBus = new EventBus();
    chainStorageClient = ChainStorageClient.memoryOnlyClient(eventBus, storageUpdateChannel);
    beaconChainUtil = BeaconChainUtil.create(16, chainStorageClient);
    beaconChainUtil.initializeStorage();

    historicalChainData = new HistoricalChainData(eventBus);
    combinedChainDataClient = new CombinedChainDataClient(chainStorageClient, historicalChainData);
    dataProvider =
        new DataProvider(
            chainStorageClient,
            combinedChainDataClient,
            p2PNetwork,
            syncService,
            validatorApiChannel);
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
