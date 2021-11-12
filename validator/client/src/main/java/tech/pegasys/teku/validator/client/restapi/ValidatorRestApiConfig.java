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

package tech.pegasys.teku.validator.client.restapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ValidatorRestApiConfig {

  public static final int DEFAULT_REST_API_PORT = 5052;
  public static final int MAX_URL_LENGTH = 65535;
  public static final String DEFAULT_REST_API_INTERFACE = "127.0.0.1";
  public static final List<String> DEFAULT_REST_API_HOST_ALLOWLIST =
      Arrays.asList("127.0.0.1", "localhost");

  private final int restApiPort;
  private final boolean restApiDocsEnabled;
  private final boolean restApiEnabled;
  private final String restApiInterface;
  private final List<String> restApiHostAllowlist;
  private final List<String> restApiCorsAllowedOrigins;
  private final int maxUrlLength;

  public ValidatorRestApiConfig(
      final int restApiPort,
      final boolean restApiDocsEnabled,
      final boolean restApiEnabled,
      final String restApiInterface,
      final List<String> restApiHostAllowlist,
      final List<String> restApiCorsAllowedOrigins,
      final int maxUrlLength) {
    this.restApiPort = restApiPort;
    this.restApiDocsEnabled = restApiDocsEnabled;
    this.restApiEnabled = restApiEnabled;
    this.restApiInterface = restApiInterface;
    this.restApiHostAllowlist = restApiHostAllowlist;
    this.restApiCorsAllowedOrigins = restApiCorsAllowedOrigins;
    this.maxUrlLength = maxUrlLength;
  }

  public static ValidatorRestApiConfigBuilder builder() {
    return new ValidatorRestApiConfigBuilder();
  }

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

  public int getMaxUrlLength() {
    return maxUrlLength;
  }

  public static final class ValidatorRestApiConfigBuilder {
    // Validator rest api
    private int restApiPort = DEFAULT_REST_API_PORT;
    private boolean restApiDocsEnabled = false;
    private boolean restApiEnabled = false;
    private String restApiInterface = DEFAULT_REST_API_INTERFACE;
    private List<String> restApiHostAllowlist = DEFAULT_REST_API_HOST_ALLOWLIST;
    private List<String> restApiCorsAllowedOrigins = Collections.emptyList();
    private int maxUrlLength = MAX_URL_LENGTH;

    public ValidatorRestApiConfigBuilder restApiPort(final int restApiPort) {
      this.restApiPort = restApiPort;
      return this;
    }

    public ValidatorRestApiConfigBuilder restApiDocsEnabled(final boolean restApiDocsEnabled) {
      this.restApiDocsEnabled = restApiDocsEnabled;
      return this;
    }

    public ValidatorRestApiConfigBuilder restApiEnabled(final boolean restApiEnabled) {
      this.restApiEnabled = restApiEnabled;
      return this;
    }

    public ValidatorRestApiConfigBuilder restApiInterface(final String restApiInterface) {
      this.restApiInterface = restApiInterface;
      return this;
    }

    public ValidatorRestApiConfigBuilder restApiHostAllowlist(
        final List<String> restApiHostAllowlist) {
      this.restApiHostAllowlist = restApiHostAllowlist;
      return this;
    }

    public ValidatorRestApiConfigBuilder restApiCorsAllowedOrigins(
        final List<String> restApiCorsAllowedOrigins) {
      this.restApiCorsAllowedOrigins = restApiCorsAllowedOrigins;
      return this;
    }

    public ValidatorRestApiConfigBuilder maxUrlLength(final int maxUrlLength) {
      this.maxUrlLength = maxUrlLength;
      return this;
    }

    public ValidatorRestApiConfig build() {
      return new ValidatorRestApiConfig(
          restApiPort,
          restApiDocsEnabled,
          restApiEnabled,
          restApiInterface,
          restApiHostAllowlist,
          restApiCorsAllowedOrigins,
          maxUrlLength);
    }
  }
}
