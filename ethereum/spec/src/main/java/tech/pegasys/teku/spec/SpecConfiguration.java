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

import tech.pegasys.teku.spec.constants.SpecConstants;

public class SpecConfiguration {
  private final SpecConstants constants;

  private SpecConfiguration(final SpecConstants constants) {
    this.constants = constants;
  }

  public static Builder builder() {
    return new Builder();
  }

  public SpecConstants constants() {
    return constants;
  }

  public static class Builder {
    private SpecConstants constants;

    public SpecConfiguration build() {
      validate();
      return new SpecConfiguration(constants);
    }

    public Builder constants(final SpecConstants constants) {
      checkNotNull(constants);
      this.constants = constants;
      return this;
    }

    private void validate() {
      checkNotNull(constants);
    }
  }
}
