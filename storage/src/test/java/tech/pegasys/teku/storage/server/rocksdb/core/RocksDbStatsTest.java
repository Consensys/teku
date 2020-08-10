package tech.pegasys.teku.storage.server.rocksdb.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.Observation;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDB;
import tech.pegasys.teku.metrics.TekuMetricCategory;

class RocksDbStatsTest {

  private final RocksDB database = mock(RocksDB.class);

  @Test
  void shouldNotCrashIfMetricsRequestedAfterClose() throws Exception {
    final ObservableMetricsSystem metricsSystem =
        PrometheusMetricsSystem.init(
            MetricsConfiguration.builder()
                .enabled(true)
                .metricCategories(Set.of(TekuMetricCategory.STORAGE_HOT_DB))
                .timersEnabled(true)
                .build());

    try (RocksDbStats stats = new RocksDbStats(metricsSystem, TekuMetricCategory.STORAGE_HOT_DB)) {
      stats.registerMetrics(database);
    }
    when(database.getLongProperty(any())).thenThrow(new IllegalStateException("Database shutdown"));
    final List<Observation> metrics =
        metricsSystem.streamObservations().collect(Collectors.toList());
    assertThat(metrics).isNotEmpty();
  }
}
