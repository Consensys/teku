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

package tech.pegasys.teku.sync.multipeer;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

public interface Sync {

  /**
   * Sync to a target chain. This may be called while a previous sync is in progress to switch the
   * sync target to a new chain.
   *
   * @param targetChain the chain to sync to
   * @return a future that completes when the sync is complete
   */
  SafeFuture<Void> syncToChain(TargetChain targetChain);
}
