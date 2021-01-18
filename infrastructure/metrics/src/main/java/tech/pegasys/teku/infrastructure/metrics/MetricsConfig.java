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

package tech.pegasys.teku.infrastructure.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class MetricsConfig {

  private final boolean metricsEnabled;
  private final int metricsPort;
  private final String metricsInterface;
  private final Set<MetricCategory> metricsCategories;
  private final List<String> metricsHostAllowlist;

  private MetricsConfig(
      final boolean metricsEnabled,
      final int metricsPort,
      final String metricsInterface,
      final Set<MetricCategory> metricsCategories,
      final List<String> metricsHostAllowlist) {
    this.metricsEnabled = metricsEnabled;
    this.metricsPort = metricsPort;
    this.metricsInterface = metricsInterface;
    this.metricsCategories = metricsCategories;
    this.metricsHostAllowlist = metricsHostAllowlist;
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

  public static final class MetricsConfigBuilder {

    private boolean metricsEnabled;
    private int metricsPort;
    private String metricsInterface;
    private Set<MetricCategory> metricsCategories = new HashSet<>();
    private List<String> metricsHostAllowlist;

    private MetricsConfigBuilder() {}

    public MetricsConfigBuilder metricsEnabled(boolean metricsEnabled) {
      this.metricsEnabled = metricsEnabled;
      return this;
    }

    public MetricsConfigBuilder metricsPort(int metricsPort) {
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

    public MetricsConfig build() {
      return new MetricsConfig(
          metricsEnabled, metricsPort, metricsInterface, metricsCategories, metricsHostAllowlist);
    }
  }
}
