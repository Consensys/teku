/*
 * Copyright Consensys Software Inc., 2025
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;

public class ReflectionBasedBeaconRestApiOptionsTest extends AbstractBeaconNodeCommandTest {

  private BeaconRestApiConfig getConfig(final TekuConfiguration tekuConfiguration) {
    return tekuConfiguration.beaconChain().beaconRestApiConfig();
  }

  @Test
  public void shouldReadFromConfigurationFile() {
    final BeaconRestApiConfig config =
        getConfig(getTekuConfigurationFromFile("beaconRestApiOptions_config.yaml"));

    assertThat(config.getRestApiInterface()).isEqualTo("127.100.0.1");
    assertThat(config.getRestApiPort()).isEqualTo(5055);
    assertThat(config.isRestApiDocsEnabled()).isTrue();
    assertThat(config.isRestApiEnabled()).isTrue();
    assertThat(config.isRestApiLightClientEnabled()).isTrue();
    assertThat(config.getRestApiHostAllowlist()).containsExactly("test.domain.com", "11.12.13.14");
    assertThat(config.getRestApiCorsAllowedOrigins())
        .containsExactly("127.1.2.3", "origin.allowed.com");
    assertThat(config.getMaxUrlLength()).isEqualTo(65535);
  }

  @Test
  public void getBlobsApiRelatedConfig_defaultsAreCorrect() {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments();
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.isGetBlobsApiP2pSidecarDownloadEnabled()).isFalse();
    assertThat(config.getGetBlobsApiP2pSidecarDownloadTimeoutSeconds())
        .isGreaterThanOrEqualTo(Duration.ZERO);
  }

  @Test
  public void getBlobsApiP2pSidecarDownloadEnabled_canBeEnabled() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--get-blobs-api-p2p-sidecars-download-enabled");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.isGetBlobsApiP2pSidecarDownloadEnabled()).isTrue();
  }

  @Test
  public void getBlobsApiP2pSidecarDownloadTimeoutSeconds_canChanged() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--get-blobs-sidecars-download-timeout", "12");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getGetBlobsApiP2pSidecarDownloadTimeoutSeconds())
        .isEqualTo(Duration.ofSeconds(12));
  }

  @Test
  public void getBlobsApiP2pSidecarDownloadTimeoutSeconds_wrongValues() {
    assertThatThrownBy(
        () -> getTekuConfigurationFromArguments("--get-blobs-sidecars-download-timeout", "0"));

    assertThatThrownBy(
        () -> getTekuConfigurationFromArguments("--get-blobs-sidecars-download-timeout", "-2"));
  }

  @Test
  public void restApiDocsEnabled_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-docs-enabled");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.isRestApiDocsEnabled()).isTrue();
  }

  @Test
  public void restApiEnabled_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments("--rest-api-enabled");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.isRestApiEnabled()).isTrue();
  }

  @ParameterizedTest
  @MethodSource("getRestApiOptionParams")
  public void restApiEnabledAndPortOptions_shouldProvideExpectedOutcome(
      final String[] options, final boolean expectedRestApiEnabled, final int expectedRestApiPort) {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments(options);
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.isRestApiEnabled()).isEqualTo(expectedRestApiEnabled);
    assertThat(config.getRestApiPort()).isEqualTo(expectedRestApiPort);
  }

  public static Stream<Arguments> getRestApiOptionParams() {
    return Stream.of(
        Arguments.of(new String[] {}, false, 5051),
        Arguments.of(new String[] {"--rest-api-port=5058"}, true, 5058),
        Arguments.of(new String[] {"--rest-api-enabled=true"}, true, 5051),
        Arguments.of(new String[] {"--rest-api-enabled=true", "--rest-api-port=5058"}, true, 5058),
        Arguments.of(
            new String[] {"--rest-api-enabled=false", "--rest-api-port=5058"}, false, 5058));
  }

  @Test
  public void restApiLightClientEnabled_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xrest-api-light-client-enabled");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.isRestApiLightClientEnabled()).isTrue();
  }

  @Test
  public void restApiHostAllowlist_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-host-allowlist");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getRestApiHostAllowlist()).isEmpty();
  }

  @Test
  public void restApiHostAllowlist_shouldSupportAllowingMultipleHosts() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-host-allowlist", "my.host,their.host");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getRestApiHostAllowlist()).containsOnly("my.host", "their.host");
  }

  @Test
  public void restApiHostAllowlist_shouldSupportAllowingAllHosts() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-host-allowlist", "*");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getRestApiHostAllowlist()).containsOnly("*");
  }

  @Test
  public void restApiHostAllowlist_shouldDefaultToLocalhost() {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments();
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getRestApiHostAllowlist()).containsOnly("localhost", "127.0.0.1");
  }

  @Test
  public void restApiCorsAllowedOrigins_shouldNotRequireAValue() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-cors-origins");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getRestApiCorsAllowedOrigins()).isEmpty();
  }

  @Test
  public void restApiCorsAllowedOrigins_shouldSupportAllowingMultipleHosts() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-cors-origins", "my.host,their.host");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getRestApiCorsAllowedOrigins()).containsOnly("my.host", "their.host");
  }

  @Test
  public void restApiCorsAllowedOrigins_shouldSupportAllowingAllHosts() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--rest-api-cors-origins", "*");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getRestApiCorsAllowedOrigins()).containsOnly("*");
  }

  @Test
  public void maxUrlLength_shouldAcceptLowerBound() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xrest-api-max-url-length", "4096");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getMaxUrlLength()).isEqualTo(4096);
  }

  @Test
  public void maxUrlLength_shouldAcceptUpperBound() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xrest-api-max-url-length", "1052672");
    final BeaconRestApiConfig config = getConfig(tekuConfiguration);
    assertThat(config.getMaxUrlLength()).isEqualTo(1052672);
  }

  @Test
  public void maxUrlLength_shouldEnforceMinimumLength() {
    final String[] args = {"--Xrest-api-max-url-length", "2047"};
    beaconNodeCommand.parse(args);
    final String output = getCommandLineOutput();
    assertThat(output).contains("Invalid value '2047'");
  }

  @Test
  public void maxUrlLength_shouldRejectNegativeNumbers() {
    final String[] args = {"--Xrest-api-max-url-length", "-2048"};
    beaconNodeCommand.parse(args);
    final String output = getCommandLineOutput();
    assertThat(output).contains("Invalid value '-2048'");
  }

  @Test
  public void maxUrlLength_shouldEnforceMaximumLength() {
    final String[] args = {"--Xrest-api-max-url-length", "1052673"};
    beaconNodeCommand.parse(args);
    final String output = getCommandLineOutput();
    assertThat(output).contains("Invalid value '1052673'");
  }

  @Test
  void validatorThreads_shouldDefaultToAtLeastOne() {
    final int validatorThreads =
        getConfig(getTekuConfigurationFromArguments()).getValidatorThreads();
    assertThat(validatorThreads).isGreaterThanOrEqualTo(1);
  }

  @Test
  void validatorThreads_shouldBeAbleToOverride() {
    TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments("--Xrest-api-validator-threads=15");
    final int validatorThreads = getConfig(tekuConfiguration).getValidatorThreads();
    assertThat(validatorThreads).isEqualTo(15);
  }
}
