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

package tech.pegasys.artemis.cli.options;

import picocli.CommandLine;

public class DepositOptions {

  @CommandLine.Option(
      names = {"--eth1-deposit-contract-address"},
      paramLabel = "<ADDRESS>",
      description = "Contract address for the deposit contract",
      arity = "1")
  private String eth1DepositContractAddress = null; // Depends on network configuration

  @CommandLine.Option(
      names = {"--eth1-endpoint"},
      paramLabel = "<NETWORK>",
      description = "URL for Eth 1.0 node",
      arity = "1")
  private String eth1Endpoint = null;

  public String getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public String getEth1Endpoint() {
    return eth1Endpoint;
  }
}
