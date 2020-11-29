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

package tech.pegasys.teku.cli.options;

import picocli.CommandLine.Option;
import tech.pegasys.teku.util.config.Eth1Address;

public class DepositOptions {

  @Option(
      names = {"--eth1-deposit-contract-address"},
      paramLabel = "<ADDRESS>",
      description =
          "Contract address for the deposit contract. Generally defaults if network has been specified.",
      arity = "1")
  private Eth1Address eth1DepositContractAddress = null; // Depends on network configuration

  @Option(
      names = {"--eth1-endpoint"},
      paramLabel = "<NETWORK>",
      description = "URL for Eth1 node.",
      arity = "1")
  private String eth1Endpoint = null;

  @Option(
      hidden = true,
      names = {"--Xeth1-deposits-from-storage-enabled"},
      defaultValue = "true",
      paramLabel = "<BOOLEAN>",
      fallbackValue = "true",
      description =
          "On startup, use Eth1 deposits from storage before loading from the remote endpoint.",
      arity = "0..1")
  private boolean eth1DepositsFromStorageEnabled = true;

  public Eth1Address getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public String getEth1Endpoint() {
    return eth1Endpoint;
  }

  public boolean isEth1DepositsFromStorageEnabled() {
    return eth1DepositsFromStorageEnabled;
  }
}
