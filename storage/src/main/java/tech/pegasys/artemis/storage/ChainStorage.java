/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import net.consensys.cava.kv.MapKeyValueStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;

public class ChainStorage {
  private MapKeyValueStore unprocessedStore;
  private final String blockKey = "BLOCK";
  private final String attestaionKey = "ATTESTATION";
  private final EventBus eventBus;
  private static final Logger LOG = LogManager.getLogger();

  public ChainStorage(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);

    this.unprocessedStore = new MapKeyValueStore();
  }

  @Subscribe
  public void onNewBlock(BeaconBlock block) {
    LOG.info("ChainStore - New Beacon Block Event detected");
    /*unprocessedStore.putAsync(Bytes.wrap(blockKey.getBytes(Charset.defaultCharset())), null);*/
  }

  @Subscribe
  public void onNewAttestation(Attestation attestation) {
    LOG.info("ChainStore - New Attestation Event detected");
    /*unprocessedStore.putAsync(Bytes.wrap(attestaionKey.getBytes(Charset.defaultCharset())), attestation.toBytes());*/
  }
}
