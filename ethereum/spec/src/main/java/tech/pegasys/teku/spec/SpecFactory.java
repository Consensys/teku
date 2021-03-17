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

import tech.pegasys.teku.spec.config.SpecConstants;
import tech.pegasys.teku.spec.config.SpecConstantsLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class SpecFactory {

  public static Spec create(final Eth2Network network) {
    return create(network.constantsName());
  }

  public static Spec createMinimal() {
    return create(Eth2Network.MINIMAL);
  }

  public static Spec createMainnet() {
    return create(Eth2Network.MAINNET);
  }

  public static Spec create(final String constantsName) {
    final SpecConstants constants = SpecConstantsLoader.loadConstants(constantsName);
    return create(constants);
  }

  public static Spec create(final SpecConstants specConstants) {
    final SpecConfiguration specConfig =
        SpecConfiguration.builder().constants(specConstants).build();
    return Spec.create(specConfig);
  }
}
