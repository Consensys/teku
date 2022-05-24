/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

/** Applies */
public class TickProcessor {

  private final Spec spec;
  private final RecentChainData recentChainData;

  private UInt64 highestProcessedTime = UInt64.ZERO;

  public TickProcessor(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public synchronized SafeFuture<Void> onTick(final UInt64 currentTimeMillis) {
    if (currentTimeMillis.isLessThanOrEqualTo(highestProcessedTime)) {
      return SafeFuture.COMPLETE;
    }
    highestProcessedTime = currentTimeMillis;
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    spec.onTick(transaction, currentTimeMillis);
    // TODO: The changes made in this transaction aren't actually applied until the commit completes
    // which may mean later updates are wrong (removing wrong proposer boost mostly).
    return transaction.commit();
  }
}
