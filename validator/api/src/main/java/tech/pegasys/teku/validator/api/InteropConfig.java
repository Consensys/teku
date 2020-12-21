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

package tech.pegasys.teku.validator.api;

import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

public class InteropConfig {

  private final Integer interopGenesisTime;
  private final int interopOwnedValidatorStartIndex;
  private final int interopOwnedValidatorCount;
  private final int interopNumberOfValidators;
  private final boolean interopEnabled;

  private InteropConfig(
      final Integer interopGenesisTime,
      final int interopOwnedValidatorStartIndex,
      final int interopOwnedValidatorCount,
      final int interopNumberOfValidators,
      final boolean interopEnabled) {
    this.interopGenesisTime = interopGenesisTime;
    this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
    this.interopOwnedValidatorCount = interopOwnedValidatorCount;
    this.interopNumberOfValidators = interopNumberOfValidators;
    this.interopEnabled = interopEnabled;
  }

  public static InteropConfigBuilder builder() {
    return new InteropConfigBuilder();
  }

  public Integer getInteropGenesisTime() {
    if (interopGenesisTime == 0) {
      return Math.toIntExact((System.currentTimeMillis() / 1000) + 5);
    } else {
      return interopGenesisTime;
    }
  }

  public int getInteropOwnedValidatorStartIndex() {
    return interopOwnedValidatorStartIndex;
  }

  public int getInteropOwnedValidatorCount() {
    return interopOwnedValidatorCount;
  }

  public int getInteropNumberOfValidators() {
    return interopNumberOfValidators;
  }

  public boolean isInteropEnabled() {
    return interopEnabled;
  }

  public static final class InteropConfigBuilder {

    private Integer interopGenesisTime;
    private int interopOwnedValidatorStartIndex;
    private int interopOwnedValidatorCount;
    private int interopNumberOfValidators = 64;
    private boolean interopEnabled;

    private InteropConfigBuilder() {}

    public InteropConfigBuilder interopGenesisTime(Integer interopGenesisTime) {
      this.interopGenesisTime = interopGenesisTime;
      return this;
    }

    public InteropConfigBuilder interopOwnedValidatorStartIndex(
        int interopOwnedValidatorStartIndex) {
      this.interopOwnedValidatorStartIndex = interopOwnedValidatorStartIndex;
      return this;
    }

    public InteropConfigBuilder interopOwnedValidatorCount(int interopOwnedValidatorCount) {
      this.interopOwnedValidatorCount = interopOwnedValidatorCount;
      return this;
    }

    public InteropConfigBuilder interopNumberOfValidators(int interopNumberOfValidators) {
      this.interopNumberOfValidators = interopNumberOfValidators;
      return this;
    }

    public InteropConfigBuilder interopEnabled(boolean interopEnabled) {
      this.interopEnabled = interopEnabled;
      return this;
    }

    public InteropConfig build() {
      validate();
      return new InteropConfig(
          interopGenesisTime,
          interopOwnedValidatorStartIndex,
          interopOwnedValidatorCount,
          interopNumberOfValidators,
          interopEnabled);
    }

    private void validate() throws IllegalArgumentException {
      if (interopNumberOfValidators < Constants.SLOTS_PER_EPOCH) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid configuration. Interop number of validators [%d] must be greater than or equal to [%d]",
                interopNumberOfValidators, Constants.SLOTS_PER_EPOCH));
      }
    }
  }
}
