/*
 * Copyright 2019 ConsenSys AG.
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

import java.util.Optional;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public enum TekuMetricCategory implements MetricCategory {
  BEACON("beacon"),
  DISCOVERY("discovery"),
  EVENTBUS("eventbus"),
  EXECUTOR("executor"),
  LIBP2P("libp2p"),
  NETWORK("network"),
  STORAGE("storage"),
  STORAGE_HOT_DB("storage_hot"),
  STORAGE_FINALIZED_DB("storage_finalized"),
  REMOTE_VALIDATOR("remote_validator"),
  VALIDATOR("validator"),
  VALIDATOR_PERFORMANCE("validator_performance");

  private final String name;

  TekuMetricCategory(final String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Optional<String> getApplicationPrefix() {
    return Optional.empty();
  }
}
