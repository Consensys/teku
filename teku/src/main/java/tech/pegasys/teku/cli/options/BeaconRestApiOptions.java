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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;

public class BeaconRestApiOptions {

  public static final int DEFAULT_REST_API_PORT = 5051;

  @Option(
      names = {"--rest-api-port"},
      paramLabel = "<INTEGER>",
      description = "Port number of Beacon Rest API",
      arity = "1")
  private int restApiPort = DEFAULT_REST_API_PORT;

  @Option(
      names = {"--rest-api-docs-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable swagger-docs and swagger-ui endpoints",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiDocsEnabled = false;

  @Option(
      names = {"--rest-api-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables Beacon Rest API",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiEnabled = false;

  @Option(
      names = {"--rest-api-interface"},
      paramLabel = "<NETWORK>",
      description = "Interface of Beacon Rest API",
      arity = "1")
  private String restApiInterface = "127.0.0.1";

  @Option(
      names = {"--rest-api-host-allowlist"},
      paramLabel = "<hostname>",
      description = "Comma-separated list of hostnames to allow, or * to allow any host",
      split = ",",
      arity = "0..*")
  private final List<String> restApiHostAllowlist = Arrays.asList("127.0.0.1", "localhost");

  @Option(
      names = {"--rest-api-cors-origins"},
      paramLabel = "<origin>",
      description = "Comma separated list of origins to allow, or * to allow any origin",
      split = ",",
      arity = "0..*")
  private final List<String> restApiCorsAllowedOrigins = new ArrayList<>();

  public int getRestApiPort() {
    return restApiPort;
  }

  public boolean isRestApiDocsEnabled() {
    return restApiDocsEnabled;
  }

  public boolean isRestApiEnabled() {
    return restApiEnabled;
  }

  public String getRestApiInterface() {
    return restApiInterface;
  }

  public List<String> getRestApiHostAllowlist() {
    return restApiHostAllowlist;
  }

  public List<String> getRestApiCorsAllowedOrigins() {
    return restApiCorsAllowedOrigins;
  }

  public void configure(final TekuConfiguration.Builder builder) {
    builder.restApi(
        restApiBuilder ->
            restApiBuilder
                .restApiEnabled(restApiEnabled)
                .restApiDocsEnabled(restApiDocsEnabled)
                .restApiPort(restApiPort)
                .restApiInterface(restApiInterface)
                .restApiHostAllowlist(restApiHostAllowlist)
                .restApiCorsAllowedOrigins(restApiCorsAllowedOrigins));
  }
}
