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
      names = {"--remote-validator-api-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables Remote Validator API",
      fallbackValue = "true",
      arity = "0..1")
  private boolean apiEnabled = false;

  @Option(
      names = {"--remote-validator-api-port"},
      paramLabel = "<INTEGER>",
      description = "Port number of Remote Validator API",
      arity = "1")
  private int apiPort = 9999;

  @Option(
      names = {"--remote-validator-api-interface"},
      paramLabel = "<NETWORK>",
      description = "Interface of Remote Validator API",
      arity = "1")
  private String apiInterface = "127.0.0.1";

  public int getApiPort() {
    return apiPort;
  }

  public boolean isApiEnabled() {
    return apiEnabled;
  }

  public String getApiInterface() {
    return apiInterface;
  }
}
