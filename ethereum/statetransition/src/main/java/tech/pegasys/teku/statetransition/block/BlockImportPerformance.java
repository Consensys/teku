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

package tech.pegasys.teku.statetransition.block;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockImportPerformance {
  private final TimeProvider timeProvider;

  private final List<Pair<String, UInt64>> events = new ArrayList<>();
  private UInt64 timeWarningLimitTimeStamp;
  private UInt64 timeAtSlotStartTimeStamp;

  public BlockImportPerformance(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
  }

  public void arrival(final RecentChainData recentChainData, final UInt64 slot) {
    timeAtSlotStartTimeStamp = secondsToMillis(recentChainData.computeTimeAtSlot(slot));
    timeWarningLimitTimeStamp =
        timeAtSlotStartTimeStamp.plus(
            secondsToMillis(recentChainData.getSpec().getSecondsPerSlot(slot)).dividedBy(3));
    addEvent("Received");
  }

  public void preStateRetrieved() {
    addEvent("Pre-state retrieved");
  }

  public void postStateCreated() {
    addEvent("Block processed");
  }

  public void transactionReady() {
    addEvent("Transaction prepared");
  }

  public void transactionCommitted() {
    addEvent("Transaction committed");
  }

  public void processingComplete(final EventLogger eventLogger, final SignedBeaconBlock block) {
    final UInt64 importCompletedTimestamp = addEvent("Import complete");

    if (importCompletedTimestamp.isGreaterThan(timeWarningLimitTimeStamp)) {
      UInt64 previousEventTimestamp = timeAtSlotStartTimeStamp;
      final List<String> eventTimings = new ArrayList<>();
      for (Pair<String, UInt64> event : events) {
        // minusMinZero because sometimes time does actually go backwards so be safe.
        final UInt64 stepDuration = event.right().minusMinZero(previousEventTimestamp);
        eventTimings.add(
            event.left() + (eventTimings.isEmpty() ? " " : " +") + stepDuration + "ms");
        previousEventTimestamp = event.right();
      }
      final String combinedTimings = String.join(", ", eventTimings);
      eventLogger.lateBlockImport(block.getRoot(), block.getSlot(), combinedTimings);
    }
  }

  private UInt64 addEvent(final String label) {
    final UInt64 timestamp = timeProvider.getTimeInMillis();
    events.add(Pair.of(label, timestamp));
    return timestamp;
  }
}
