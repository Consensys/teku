/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.coordinator;

import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class FutureBlockProductionPreparationTrigger {
  public static final FutureBlockProductionPreparationTrigger NOOP =
      new FutureBlockProductionPreparationTrigger(null, null, null) {
        @Override
        public void onFutureBlockProductionPreparationDue(final UInt64 slot) {}
      };

  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Consumer<UInt64> futureBlockProductionPreparator;
  private final AsyncRunner asyncRunner;

  private volatile boolean inSync;

  public FutureBlockProductionPreparationTrigger(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final Consumer<UInt64> futureBlockProductionPreparator) {
    this.recentChainData = recentChainData;
    this.futureBlockProductionPreparator = futureBlockProductionPreparator;
    this.asyncRunner = asyncRunner;
  }

  public void onFutureBlockProductionPreparationDue(final UInt64 slot) {
    if (!inSync) {
      return;
    }
    asyncRunner
        .runAsync(
            () -> {
              final UInt64 productionSlot = slot.increment();
              recentChainData
                  .isBlockProposerConnected(productionSlot)
                  .thenAccept(
                      isConnected -> {
                        if (isConnected) {
                          futureBlockProductionPreparator.accept(productionSlot);
                        }
                      })
                  .finishError(LOG);
            })
        .finishError(LOG);
  }

  public void onSyncingStatusChanged(final boolean inSync) {
    this.inSync = inSync;
  }
}
