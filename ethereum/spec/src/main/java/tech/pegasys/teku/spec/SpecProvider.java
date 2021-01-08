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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecProvider {
  // Eventually we will have multiple versioned specs, where each version is active for a specific
  // range of epochs
  private final Spec initialSpec;

  private SpecProvider(final Spec initialSpec) {
    this.initialSpec = initialSpec;
  }

  public static SpecProvider create(final SpecConfiguration config) {
    final Spec initialSpec = new Spec(config.constants());
    return new SpecProvider(initialSpec);
  }

  public Spec get(final UInt64 epoch) {
    return initialSpec;
  }
}
