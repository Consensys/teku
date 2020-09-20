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

public class RemoteValidatorApiOptions {

  @Option(
      names = {"--Xremote-validator-api-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables Remote Validator API",
      fallbackValue = "true",
      arity = "0..1",
      hidden = true)
  private boolean apiEnabled = false;

  @Option(
      names = {"--Xremote-validator-api-port"},
      paramLabel = "<INTEGER>",
      description = "Port number of Remote Validator API",
      arity = "1",
      hidden = true)
  private int apiPort = 9999;

  @Option(
      names = {"--Xremote-validator-api-interface"},
      paramLabel = "<NETWORK>",
      description = "Interface of Remote Validator API",
      arity = "1",
      hidden = true)
  private String apiInterface = "127.0.0.1";

  @Option(
      names = {"--Xremote-validator-api-max-subscribers"},
      paramLabel = "<INTEGER>",
      description = "Maximum of Validator nodes connected to the API",
      arity = "0..1",
      hidden = true)
  private int maxSubscribers = 1_000;

  public int getApiPort() {
    return apiPort;
  }

  public boolean isApiEnabled() {
    return apiEnabled;
  }

  public String getApiInterface() {
    return apiInterface;
  }

  public int getMaxSubscribers() {
    return maxSubscribers;
  }
}
