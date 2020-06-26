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

package tech.pegasys.teku.storage.storageSystem;

import java.util.List;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AbstractStorageSystem implements StorageSystem {
  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  protected final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  protected final ChainUpdater chainUpdater;

  protected final RecentChainData recentChainData;

  protected AbstractStorageSystem(RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
    chainUpdater = new ChainUpdater(this.recentChainData, chainBuilder);
  }

  @Override
  public RecentChainData recentChainData() {
    return recentChainData;
  }

  @Override
  public ChainBuilder chainBuilder() {
    return chainBuilder;
  }

  @Override
  public ChainUpdater chainUpdater() {
    return chainUpdater;
  }
}
