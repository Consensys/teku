/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.cli.options;

import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;

public class MinimalEth2NetworkOptions {

  @Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use.",
      arity = "1")
  private String network = "mainnet";

  @Option(
          names = {"--Xtrusted-setup"},
          hidden = true,
          paramLabel = "<STRING>",
          description =
                  "The trusted setup which is needed for KZG commitments. Only required when creating a custom network. This value should be a file or URL pointing to a trusted setup.",
          arity = "1")
  private String trustedSetup = null; // Depends on network configuration


  public void configure(final TekuConfiguration.Builder builder) {
    builder.eth2NetworkConfig(b -> b.applyNetworkDefaults(network));
  }

  public Spec getSpec() {
    return getConfig().getSpec();
  }

  private Eth2NetworkConfiguration getConfig() {
    Eth2NetworkConfiguration.Builder builder = Eth2NetworkConfiguration.builder(network);
    if (trustedSetup != null) {
      builder.trustedSetup(trustedSetup);
    }
     return builder.build();
  }
}
