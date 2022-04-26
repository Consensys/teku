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
import java.util.Locale;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockImportPerformance {
  public static final String ARRIVAL_EVENT_LABEL = "arrival";
  public static final String PRESTATE_RETRIEVED_EVENT_LABEL = "pre-state_retrieved";
  public static final String PROCESSED_EVENT_LABEL = "processed";
  public static final String TRANSACTION_PREPARED_EVENT_LABEL = "transaction_prepared";
  public static final String TRANSACTION_COMMITTED_EVENT_LABEL = "transaction_committed";
  public static final String COMPLETED_EVENT_LABEL = "completed";

  public static final String TOTAL_PROCESSING_TIME_LABEL = "total_processing_time";

  public static final String SUCCESS_RESULT_METRIC_LABEL_VALUE = "success";

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
    addEvent(ARRIVAL_EVENT_LABEL);
  }

  public void preStateRetrieved() {
    addEvent(PRESTATE_RETRIEVED_EVENT_LABEL);
  }

  public void postStateCreated() {
    addEvent(PROCESSED_EVENT_LABEL);
  }

  public void transactionReady() {
    addEvent(TRANSACTION_PREPARED_EVENT_LABEL);
  }

  public void transactionCommitted() {
    addEvent(TRANSACTION_COMMITTED_EVENT_LABEL);
  }

  public void processingComplete(
      final EventLogger eventLogger,
      final SignedBeaconBlock block,
      final BlockImportResult blockImportResult) {
    final UInt64 importCompletedTimestamp = addEvent(COMPLETED_EVENT_LABEL);

    final List<String> lateBlockEventTimings = new ArrayList<>();
    final boolean isLateEvent = importCompletedTimestamp.isGreaterThan(timeWarningLimitTimeStamp);

    final String resultMetricLabelValue =
        blockImportResult.isSuccessful()
            ? SUCCESS_RESULT_METRIC_LABEL_VALUE
            : blockImportResult.getFailureReason().name().toLowerCase(Locale.ROOT);

    UInt64 previousEventTimestamp = timeAtSlotStartTimeStamp;
    for (Pair<String, UInt64> event : events) {

      // minusMinZero because sometimes time does actually go backwards so be safe.
      final UInt64 stepDuration = event.right().minusMinZero(previousEventTimestamp);
      previousEventTimestamp = event.right();

      blockImportMetrics.recordValue(stepDuration, event.left(), resultMetricLabelValue);

      if (isLateEvent) {
        lateBlockEventTimings.add(
            event.left() + (lateBlockEventTimings.isEmpty() ? " " : " +") + stepDuration + "ms");
      }
    }

    final UInt64 totalProcessingDuration =
        importCompletedTimestamp.minusMinZero(events.get(0).right());
    blockImportMetrics.recordValue(
        totalProcessingDuration, TOTAL_PROCESSING_TIME_LABEL, resultMetricLabelValue);

    if (isLateEvent) {
      final String combinedTimings = String.join(", ", lateBlockEventTimings);
      eventLogger.lateBlockImport(
          block.getRoot(), block.getSlot(), block.getProposerIndex(), combinedTimings);
    }
  }

  private UInt64 addEvent(final String label) {
    final UInt64 timestamp = timeProvider.getTimeInMillis();
    events.add(Pair.of(label, timestamp));
    return timestamp;
  }
}
