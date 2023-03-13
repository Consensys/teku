/*
 * Copyright ConsenSys Software Inc., 2023
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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.StorageQueryChannel;

public class EarliestAvailableBlockSlot {
  private static final Logger LOG = LogManager.getLogger();
  private final StorageQueryChannel historicalChainData;
  private final TimeProvider timeProvider;

  private final int earliestAvailableBlockSlotFrequency;

  private UInt64 queryTime = UInt64.ZERO;

  private SafeFuture<Optional<UInt64>> earliestAvailableBlockSlotFuture =
      SafeFuture.completedFuture(Optional.empty());

  public EarliestAvailableBlockSlot(
      StorageQueryChannel historicalChainData,
      TimeProvider timeProvider,
      int earliestAvailableBlockSlotFrequency) {
    this.historicalChainData = historicalChainData;
    this.timeProvider = timeProvider;
    this.earliestAvailableBlockSlotFrequency = earliestAvailableBlockSlotFrequency;
  }

  public SafeFuture<Optional<UInt64>> get() {
    if (!earliestAvailableBlockSlotFuture.isDone()) {
      // if the current query is not done, we should just return the same future
      return earliestAvailableBlockSlotFuture;
    }

    if (queryTime
        .plus(earliestAvailableBlockSlotFrequency)
        .isLessThanOrEqualTo(timeProvider.getTimeInSeconds())) {
      return actuallyGetEarliestBlockSlot();
    }
    return earliestAvailableBlockSlotFuture;
  }

  private synchronized SafeFuture<Optional<UInt64>> actuallyGetEarliestBlockSlot() {
    final UInt64 timeInSeconds = timeProvider.getTimeInSeconds();
    if (queryTime.plus(earliestAvailableBlockSlotFrequency).isLessThanOrEqualTo(timeInSeconds)) {
      queryTime = timeInSeconds;
      LOG.trace("Setting earliestBlockSlot query time to {}", queryTime);
    } else {
      return earliestAvailableBlockSlotFuture;
    }
    if (!earliestAvailableBlockSlotFuture.isDone()) {
      // if the current query is not done, we should just return the same future
      return earliestAvailableBlockSlotFuture;
    }
    LOG.trace("Fetching earliestBlockSlot...");
    earliestAvailableBlockSlotFuture = historicalChainData.getEarliestAvailableBlockSlot();
    return earliestAvailableBlockSlotFuture;
  }
}
