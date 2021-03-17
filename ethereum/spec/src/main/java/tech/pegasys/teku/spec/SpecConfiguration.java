/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.spec.config.SpecConfig;

public class SpecConfiguration {
  private final SpecConfig config;

  private SpecConfiguration(final SpecConfig config) {
    this.config = config;
  }

  public static Builder builder() {
    return new Builder();
  }

  public SpecConfig config() {
    return config;
  }

  public static class Builder {
    private SpecConfig config;

    public SpecConfiguration build() {
      validate();
      return new SpecConfiguration(config);
    }

    public Builder config(final SpecConfig config) {
      checkNotNull(config);
      this.config = config;
      return this;
    }

    private void validate() {
      checkNotNull(config);
    }
  }
}
