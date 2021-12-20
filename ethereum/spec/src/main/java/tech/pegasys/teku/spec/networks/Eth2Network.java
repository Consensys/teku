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

package tech.pegasys.teku.spec.networks;

import java.util.Locale;
import java.util.Optional;

public enum Eth2Network {
  // Live networks
  MAINNET,
  PYRMONT,
  PRATER,
  KINTSUGI,
  // Test networks
  MINIMAL,
  SWIFT,
  LESS_SWIFT;

  public String configName() {
    // TODO(#3356) - Store config with filename matching enum so we don't have to convert
    return name().toLowerCase(Locale.US).replace("_", "-");
  }

  public static Eth2Network fromString(final String networkName) {
    return fromStringLenient(networkName)
        .orElseThrow(() -> new IllegalArgumentException("Unknown network: " + networkName));
  }

  public static Optional<Eth2Network> fromStringLenient(final String networkName) {
    final String normalizedNetworkName =
        networkName.strip().toUpperCase(Locale.US).replace("-", "_");
    for (Eth2Network value : values()) {
      if (value.name().equals(normalizedNetworkName)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
