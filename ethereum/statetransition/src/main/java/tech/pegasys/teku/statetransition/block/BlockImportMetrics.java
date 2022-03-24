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

import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.BLOCK_PROCESSED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.IMPORT_COMPLETED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.PRESTATE_RETRIEVED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.RECEIVED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TRANSACTION_COMMITTED_EVENT_LABEL;
import static tech.pegasys.teku.statetransition.block.BlockImportPerformance.TRANSACTION_PREPARED_EVENT_LABEL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogramWithCounters;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockImportMetrics {
  private final Map<String, MetricsHistogramWithCounters> eventsToMetric = new HashMap<>();

  public BlockImportMetrics(final MetricsSystem metricsSystem) {

    eventsToMetric.put(
        RECEIVED_EVENT_LABEL,
        MetricsHistogramWithCounters.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            "block_import_arrival_delay_summary",
            "Histogram recording delay in milliseconds from expected time to a block being received",
            "block_import_arrival_delay_counter",
            "Block counter recording delay in milliseconds from expected time to a block being received",
            1,
            List.of(
                UInt64.valueOf(500),
                UInt64.valueOf(1000),
                UInt64.valueOf(1500),
                UInt64.valueOf(2000),
                UInt64.valueOf(3000),
                UInt64.valueOf(4000),
                UInt64.valueOf(5000),
                UInt64.valueOf(8000),
                UInt64.valueOf(12000))));

    eventsToMetric.put(
        PRESTATE_RETRIEVED_EVENT_LABEL,
        MetricsHistogramWithCounters.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            "block_import_prestate_retrieved_delay_summary",
            "Histogram recording delay in milliseconds for block prestate retrieval",
            "block_import_prestate_retrieved_delay_counter",
            "Block counter recording delay in milliseconds for block prestate retrieval",
            1,
            List.of(
                UInt64.valueOf(50),
                UInt64.valueOf(100),
                UInt64.valueOf(250),
                UInt64.valueOf(500),
                UInt64.valueOf(1000),
                UInt64.valueOf(2000))));

    eventsToMetric.put(
        BLOCK_PROCESSED_EVENT_LABEL,
        MetricsHistogramWithCounters.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            "block_import_processing_delay_summary",
            "Histogram recording delay in milliseconds for block processing",
            "block_import_processing_delay_counter",
            "Block counter recording delay in milliseconds for block processing",
            1,
            List.of(
                UInt64.valueOf(50),
                UInt64.valueOf(100),
                UInt64.valueOf(250),
                UInt64.valueOf(500),
                UInt64.valueOf(1000),
                UInt64.valueOf(2000))));

    eventsToMetric.put(
        TRANSACTION_PREPARED_EVENT_LABEL,
        MetricsHistogramWithCounters.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            "block_import_transaction_prepared_delay_summary",
            "Histogram recording delay in milliseconds for block import transaction preparation",
            "block_import_transaction_prepared_delay_counter",
            "Block counter recording delay in milliseconds for block import transaction preparation",
            1,
            List.of(UInt64.valueOf(10))));

    eventsToMetric.put(
        TRANSACTION_COMMITTED_EVENT_LABEL,
        MetricsHistogramWithCounters.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            "block_import_transaction_committed_delay_summary",
            "Histogram recording delay in milliseconds for block import committing transaction",
            "block_import_transaction_committed_delay_counter",
            "Block counter recording delay in milliseconds for block import committing transaction",
            1,
            List.of(UInt64.valueOf(10))));

    eventsToMetric.put(
        IMPORT_COMPLETED_EVENT_LABEL,
        MetricsHistogramWithCounters.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            "block_import_completion_delay_summary",
            "Histogram recording delay in milliseconds for block import completion",
            "block_import_completion_delay_counter",
            "Block counter recording delay in milliseconds for block import completion",
            1,
            List.of(UInt64.valueOf(10))));
  }

  public void recordEvent(final String eventLabel, final UInt64 duration) {
    MetricsHistogramWithCounters metric = eventsToMetric.get(eventLabel);
    if (metric != null) {
      metric.recordValue(duration);
    }
  }
}
