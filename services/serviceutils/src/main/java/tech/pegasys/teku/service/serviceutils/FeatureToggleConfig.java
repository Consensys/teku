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

package tech.pegasys.teku.service.serviceutils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FeatureToggleConfig {
  private static final Logger LOG = LogManager.getLogger();
  private final boolean asyncStorageEnabled;

  private FeatureToggleConfig(final boolean asyncStorageEnabled) {
    this.asyncStorageEnabled = asyncStorageEnabled;
    LOG.info("Configured feature toggled. Async storage enabled: {}", asyncStorageEnabled);
  }

  public static FeatureToggleConfig.Builder builder() {
    return new Builder();
  }

  public boolean isAsyncStorageEnabled() {
    return asyncStorageEnabled;
  }

  public static final class Builder {

    private boolean asyncStorageEnabled;

    private Builder() {}

    public Builder asyncStorageEnabled(boolean asyncStorageEnabled) {
      this.asyncStorageEnabled = asyncStorageEnabled;
      return this;
    }

    public FeatureToggleConfig build() {
      return new FeatureToggleConfig(asyncStorageEnabled);
    }
  }
}
