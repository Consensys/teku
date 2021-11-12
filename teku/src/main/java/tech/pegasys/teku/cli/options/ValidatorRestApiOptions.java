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

package tech.pegasys.teku.cli.options;

import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;

public class ValidatorRestApiOptions {
  @CommandLine.Spec CommandLine.Model.CommandSpec cliSpec;

  @CommandLine.Option(
      names = {"--Xvalidator-api-port"},
      paramLabel = "<INTEGER>",
      description = "Port number of Rest API",
      hidden = true,
      arity = "1")
  private int restApiPort = ValidatorRestApiConfig.DEFAULT_REST_API_PORT;

  @CommandLine.Option(
      names = {"--Xvalidator-api-docs-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Enable swagger-docs and swagger-ui endpoints",
      hidden = true,
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiDocsEnabled = false;

  @CommandLine.Option(
      names = {"--Xvalidator-api-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Enables Validator Rest API",
      hidden = true,
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiEnabled = false;

  @CommandLine.Option(
      names = {"--Xvalidator-api-interface"},
      paramLabel = "<NETWORK>",
      description = "Interface of Validator Rest API",
      hidden = true,
      arity = "1")
  private String restApiInterface = ValidatorRestApiConfig.DEFAULT_REST_API_INTERFACE;

  @CommandLine.Option(
      names = {"--Xvalidator-api-host-allowlist"},
      paramLabel = "<hostname>",
      description = "Comma-separated list of hostnames to allow, or * to allow any host",
      hidden = true,
      split = ",",
      arity = "0..*")
  private final List<String> restApiHostAllowlist =
      ValidatorRestApiConfig.DEFAULT_REST_API_HOST_ALLOWLIST;

  @CommandLine.Option(
      names = {"--Xvalidator-api-cors-origins"},
      paramLabel = "<origin>",
      description = "Comma separated list of origins to allow, or * to allow any origin",
      hidden = true,
      split = ",",
      arity = "0..*")
  private final List<String> restApiCorsAllowedOrigins = new ArrayList<>();

  public void configure(final TekuConfiguration.Builder builder) {
    builder.validatorApi(
        validatorApiBuilder ->
            validatorApiBuilder
                .restApiEnabled(restApiEnabled)
                .restApiDocsEnabled(restApiDocsEnabled)
                .restApiPort(restApiPort)
                .restApiInterface(restApiInterface)
                .restApiCorsAllowedOrigins(restApiCorsAllowedOrigins)
                .restApiHostAllowlist(restApiHostAllowlist));
  }
}
