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

package tech.pegasys.artemis.network.p2p.jvmlibp2p;

import com.google.common.eventbus.EventBus;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class ChainStorageClientFactory {

  public static ChainStorageClient createInitedStorageClient(final EventBus eventBus) {
    final ChainStorageClient chainStorageClient = new ChainStorageClient(eventBus);
    initChainStorageClient(chainStorageClient);
    return chainStorageClient;
  }

  public static void initChainStorageClient(final ChainStorageClient chainStorageClient) {
    StartupUtil.setupInitialState(chainStorageClient, 0, null, 0);
  }
}
