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
import net.consensys.cava.bytes.Bytes;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.util.alogger.ALogger;

/** This class is the ChainStorage server-side logic */
public class ChainStorageServer extends ChainStorageClient implements ChainStorage {
  static final ALogger LOG = new ALogger(ChainStorageServer.class.getName());

  public ChainStorageServer() {}

  public ChainStorageServer(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  @Subscribe
  public void onNewProcessedBlock(Bytes blockHash, BeaconBlock block) {
    LOG.log(Level.INFO, "ChainStorage: new block processed");
    addProcessedBlock(blockHash, block);
  }
}
