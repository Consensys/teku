/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.reference;

import java.util.Objects;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

public class KzgRetriever {

  private static final String MAINNET = "mainnet";
  private static final String MINIMAL = "minimal";

  public static KZG getKzgWithLoadedTrustedSetup(final String network) {
    final String trustedSetupFilename =
        switch (network) {
          case MAINNET -> "mainnet-trusted-setup.txt";
          case MINIMAL -> "minimal-trusted-setup.txt";
          default -> throw new IllegalArgumentException("Unknown network: " + network);
        };
    final String trustedSetupFile =
        Objects.requireNonNull(Eth2NetworkConfiguration.class.getResource(trustedSetupFilename))
            .toExternalForm();
    final KZG kzg = KZG.getInstance();
    kzg.loadTrustedSetup(trustedSetupFile);
    return kzg;
  }
}
