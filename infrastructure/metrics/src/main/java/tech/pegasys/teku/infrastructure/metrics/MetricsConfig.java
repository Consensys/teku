/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.metrics;

import com.google.common.collect.ImmutableSet;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;

public class MetricsConfig {

  public static final ImmutableSet<MetricCategory> DEFAULT_METRICS_CATEGORIES =
      ImmutableSet.<MetricCategory>builder()
          .addAll(EnumSet.allOf(StandardMetricCategory.class))
          .addAll(EnumSet.allOf(TekuMetricCategory.class))
          .build();
  public static final int DEFAULT_METRICS_PORT = 8008;
  public static final String DEFAULT_METRICS_INTERFACE = "127.0.0.1";
  public static final List<String> DEFAULT_METRICS_HOST_ALLOWLIST =
      Arrays.asList("127.0.0.1", "localhost");
  public static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 60;
  public static final int DEFAULT_METRICS_PUBLICATION_INTERVAL = 60;
  public static final boolean DEFAULT_BLOCK_PERFORMANCE_ENABLED = false;
  public static final boolean DEFAULT_TICK_PERFORMANCE_ENABLED = false;
  public static final boolean DEFAULT_TX_PERFORMANCE_ENABLED = true;

  private final boolean metricsEnabled;
  private final int metricsPort;
  private final String metricsInterface;
  private final Set<MetricCategory> metricsCategories;
  private final List<String> metricsHostAllowlist;
  private final Optional<URL> metricsEndpoint;
  private final int publicationInterval;
  private final int idleTimeoutSeconds;
  private final boolean blockPerformanceEnabled;
  private final boolean tickPerformanceEnabled;
  private final boolean txPerformanceEnabled;

  private MetricsConfig(
      final boolean metricsEnabled,
      final int metricsPort,
      final String metricsInterface,
      final Set<MetricCategory> metricsCategories,
      final List<String> metricsHostAllowlist,
      final URL metricsEndpoint,
      final int publicationInterval,
      final int idleTimeoutSeconds,
      final boolean blockPerformanceEnabled,
      final boolean tickPerformanceEnabled,
      final boolean txPerformanceEnabled) {
    this.metricsEnabled = metricsEnabled;
    this.metricsPort = metricsPort;
    this.metricsInterface = metricsInterface;
    this.metricsCategories = metricsCategories;
    this.metricsHostAllowlist = metricsHostAllowlist;
    this.metricsEndpoint = Optional.ofNullable(metricsEndpoint);
    this.publicationInterval = publicationInterval;
    this.idleTimeoutSeconds = idleTimeoutSeconds;
    this.blockPerformanceEnabled = blockPerformanceEnabled;
    this.tickPerformanceEnabled = tickPerformanceEnabled;
    this.txPerformanceEnabled = txPerformanceEnabled;
  }

  public static MetricsConfigBuilder builder() {
    return new MetricsConfigBuilder();
  }

  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  public int getMetricsPort() {
    return metricsPort;
  }

  public String getMetricsInterface() {
    return metricsInterface;
  }

  public Set<MetricCategory> getMetricsCategories() {
    return metricsCategories;
  }

  public List<String> getMetricsHostAllowlist() {
    return metricsHostAllowlist;
  }

  public Optional<URL> getMetricsEndpoint() {
    return metricsEndpoint;
  }

  public int getPublicationInterval() {
    return publicationInterval;
  }

  public int getIdleTimeoutSeconds() {
    return idleTimeoutSeconds;
  }

  public boolean isBlockPerformanceEnabled() {
    return blockPerformanceEnabled;
  }

  public boolean isTickPerformanceEnabled() {
    return tickPerformanceEnabled;
  }

  public boolean isTxPerformanceEnabled() {
    return txPerformanceEnabled;
  }

  public static final class MetricsConfigBuilder {

    private boolean metricsEnabled = false;
    private int metricsPort = DEFAULT_METRICS_PORT;
    private String metricsInterface = DEFAULT_METRICS_INTERFACE;
    private Set<MetricCategory> metricsCategories = DEFAULT_METRICS_CATEGORIES;
    private List<String> metricsHostAllowlist = DEFAULT_METRICS_HOST_ALLOWLIST;
    private URL metricsPublishEndpoint = null;
    private int metricsPublishInterval = DEFAULT_METRICS_PUBLICATION_INTERVAL;
    private int idleTimeoutSeconds = DEFAULT_IDLE_TIMEOUT_SECONDS;
    private boolean blockPerformanceEnabled = DEFAULT_BLOCK_PERFORMANCE_ENABLED;
    private boolean tickPerformanceEnabled = DEFAULT_TICK_PERFORMANCE_ENABLED;
    private boolean txPerformanceEnabled = DEFAULT_TX_PERFORMANCE_ENABLED;

    private MetricsConfigBuilder() {}

    public MetricsConfigBuilder metricsEnabled(boolean metricsEnabled) {
      this.metricsEnabled = metricsEnabled;
      return this;
    }

    public MetricsConfigBuilder metricsPort(int metricsPort) {
      if (!PortAvailability.isPortValid(metricsPort)) {
        throw new InvalidConfigurationException(
            String.format("Invalid metricsPort: %d", metricsPort));
      }
      this.metricsPort = metricsPort;
      return this;
    }

    public MetricsConfigBuilder metricsInterface(String metricsInterface) {
      this.metricsInterface = metricsInterface;
      return this;
    }

    public MetricsConfigBuilder metricsCategories(Set<MetricCategory> metricsCategories) {
      this.metricsCategories = metricsCategories;
      return this;
    }

    public MetricsConfigBuilder metricsHostAllowlist(List<String> metricsHostAllowlist) {
      this.metricsHostAllowlist = metricsHostAllowlist;
      return this;
    }

    public MetricsConfigBuilder metricsPublishEndpoint(URL metricsPublishEndpoint) {
      this.metricsPublishEndpoint = metricsPublishEndpoint;
      return this;
    }

    public MetricsConfigBuilder metricsPublishInterval(int metricsPublishInterval) {
      if (metricsPublishInterval < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid metricsPublishInterval: %d", metricsPublishInterval));
      }
      this.metricsPublishInterval = metricsPublishInterval;
      return this;
    }

    public MetricsConfigBuilder idleTimeoutSeconds(final int idleTimeoutSeconds) {
      if (idleTimeoutSeconds < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid idleTimeoutSeconds: %d", idleTimeoutSeconds));
      }
      this.idleTimeoutSeconds = idleTimeoutSeconds;
      return this;
    }

    public MetricsConfigBuilder blockPerformanceEnabled(final boolean blockPerformanceEnabled) {
      this.blockPerformanceEnabled = blockPerformanceEnabled;
      return this;
    }

    public MetricsConfigBuilder tickPerformanceEnabled(final boolean tickPerformanceEnabled) {
      this.tickPerformanceEnabled = tickPerformanceEnabled;
      return this;
    }

    public MetricsConfigBuilder txPerformanceEnabled(final boolean txPerformanceEnabled) {
      this.txPerformanceEnabled = txPerformanceEnabled;
      return this;
    }

    public MetricsConfig build() {
      return new MetricsConfig(
          metricsEnabled,
          metricsPort,
          metricsInterface,
          metricsCategories,
          metricsHostAllowlist,
          metricsPublishEndpoint,
          metricsPublishInterval,
          idleTimeoutSeconds,
          blockPerformanceEnabled,
          tickPerformanceEnabled,
          txPerformanceEnabled);
    }
  }
}
