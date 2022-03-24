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
  public static String RECEIVED_EVENT_LABEL = "Received";
  public static String PRESTATE_RETRIEVED_EVENT_LABEL = "Pre-state retrieved";
  public static String BLOCK_PROCESSED_EVENT_LABEL = "Block processed";
  public static String TRANSACTION_PREPARED_EVENT_LABEL = "Transaction prepared";
  public static String TRANSACTION_COMMITTED_EVENT_LABEL = "Transaction committed";
  public static String IMPORT_COMPLETED_EVENT_LABEL = "Import complete";

  public static String TOTAL_PROCESSING_TIME_LABEL = "Total Processing time";

  private final TimeProvider timeProvider;
  private final BlockImportMetrics blockImportMetrics;

  private final List<Pair<String, UInt64>> events = new ArrayList<>();
  private UInt64 timeWarningLimitTimeStamp;
  private UInt64 timeAtSlotStartTimeStamp;

  public BlockImportPerformance(
      final TimeProvider timeProvider, final BlockImportMetrics blockImportMetrics) {
    this.timeProvider = timeProvider;
    this.blockImportMetrics = blockImportMetrics;
  }

  public void arrival(final RecentChainData recentChainData, final UInt64 slot) {
    timeAtSlotStartTimeStamp = secondsToMillis(recentChainData.computeTimeAtSlot(slot));
    timeWarningLimitTimeStamp =
        timeAtSlotStartTimeStamp.plus(
            secondsToMillis(recentChainData.getSpec().getSecondsPerSlot(slot)).dividedBy(3));
    addEvent(RECEIVED_EVENT_LABEL);
  }

  public void preStateRetrieved() {
    addEvent(PRESTATE_RETRIEVED_EVENT_LABEL);
  }

  public void postStateCreated() {
    addEvent(BLOCK_PROCESSED_EVENT_LABEL);
  }

  public void transactionReady() {
    addEvent(TRANSACTION_PREPARED_EVENT_LABEL);
  }

  public void transactionCommitted() {
    addEvent(TRANSACTION_COMMITTED_EVENT_LABEL);
  }

  public void processingComplete(final EventLogger eventLogger, final SignedBeaconBlock block) {
    final UInt64 importCompletedTimestamp = addEvent(IMPORT_COMPLETED_EVENT_LABEL);

    final List<String> lateBlockEventTimings =
        importCompletedTimestamp.isGreaterThan(timeWarningLimitTimeStamp)
            ? new ArrayList<>()
            : null;

    UInt64 previousEventTimestamp = timeAtSlotStartTimeStamp;
    Pair<String, UInt64> lastEvent;
    for (Pair<String, UInt64> event : events) {

      // minusMinZero because sometimes time does actually go backwards so be safe.
      final UInt64 stepDuration = event.right().minusMinZero(previousEventTimestamp);
      previousEventTimestamp = event.right();

      blockImportMetrics.recordEvent(event.left(), stepDuration);

      if (lateBlockEventTimings != null) {
        lateBlockEventTimings.add(
            event.left() + (lateBlockEventTimings.isEmpty() ? " " : " +") + stepDuration + "ms");
      }

      lastEvent = event;
    }

    // if(lastEvent.left().equals(IMPORT_COMPLETED_EVENT_LABEL)) {
    //   events.get(0).right()
    // }

    if (lateBlockEventTimings != null) {
      final String combinedTimings = String.join(", ", lateBlockEventTimings);
      eventLogger.lateBlockImport(block.getRoot(), block.getSlot(), combinedTimings);
    }
  }

  private UInt64 addEvent(final String label) {
    final UInt64 timestamp = timeProvider.getTimeInMillis();
    events.add(Pair.of(label, timestamp));
    return timestamp;
  }
}
