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

public class BeaconRestApiOptions {

  public static final String REST_API_PORT_OPTION_NAME = "--rest-api-port";
  public static final String REST_API_DOCS_ENABLED_OPTION_NAME = "--rest-api-docs-enabled";
  public static final String REST_API_ENABLED_OPTION_NAME = "--rest-api-enabled";
  public static final String REST_API_INTERFACE_OPTION_NAME = "--rest-api-interface";

  public static final int DEFAULT_REST_API_PORT = 5051;
  public static final boolean DEFAULT_REST_API_DOCS_ENABLED = false;
  public static final boolean DEFAULT_REST_API_ENABLED = false;
  public static final String DEFAULT_REST_API_INTERFACE = "127.0.0.1";

  @CommandLine.Option(
      names = {REST_API_PORT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Port number of Beacon Rest API",
      arity = "1")
  private int restApiPort = DEFAULT_REST_API_PORT;

  @CommandLine.Option(
      names = {REST_API_DOCS_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enable swagger-docs and swagger-ui endpoints",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiDocsEnabled = DEFAULT_REST_API_DOCS_ENABLED;

  @CommandLine.Option(
      names = {REST_API_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      description = "Enables Beacon Rest API",
      fallbackValue = "true",
      arity = "0..1")
  private boolean restApiEnabled = DEFAULT_REST_API_ENABLED;

  @CommandLine.Option(
      names = {REST_API_INTERFACE_OPTION_NAME},
      paramLabel = "<NETWORK>",
      description = "Interface of Beacon Rest API",
      arity = "1")
  private String restApiInterface = DEFAULT_REST_API_INTERFACE;

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
}
