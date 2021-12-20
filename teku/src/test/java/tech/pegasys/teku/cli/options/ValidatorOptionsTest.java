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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class ValidatorOptionsTest extends AbstractBeaconNodeCommandTest {

  // from config file ("T E K U") UTF8 -> bytes32 -> as hex string
  private final Bytes32 graffiti =
      Bytes32.fromHexString("0x542045204b205500000000000000000000000000000000000000000000000000");

  @Test
  public void shouldReadFromConfigurationFile() throws MalformedURLException {
    final ValidatorConfig config =
        getTekuConfigurationFromFile("validatorOptions_config.yaml")
            .validatorClient()
            .getValidatorConfig();

    assertThat(config.getValidatorKeys())
        .containsExactlyInAnyOrder("a.key:a.password", "b.json:b.txt");
    assertThat(config.getValidatorExternalSignerPublicKeySources())
        .containsExactly(
            "0xad113a7d152dc74ae2b26db65bfb89ed07501c818bf47671c6d34e5a2f7224e4c5525dd4fddaa93aa328da86b7205009");
    assertThat(config.getValidatorExternalSignerUrl()).isEqualTo(new URL("https://signer.url/"));
    assertThat(config.getValidatorExternalSignerTimeout()).isEqualTo(Duration.ofMillis(1234));
    assertThat(config.getGraffitiProvider().get()).isEqualTo(Optional.of(graffiti));
  }

  @Test
  public void shouldReadValidatorExternalSignerConcurrentRequestLimit() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments("--Xvalidators-external-signer-concurrent-limit=123")
            .validatorClient()
            .getValidatorConfig();
    assertThat(config.getValidatorExternalSignerConcurrentRequestLimit()).isEqualTo(123);
  }

  @Test
  public void graffiti_shouldBeEmptyByDefault() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments().validatorClient().getValidatorConfig();
    assertThat(config.getGraffitiProvider().get()).isEmpty();
  }

  @Test
  void shouldEnableSlashingProtectionForExternalSignersByDefault() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments().validatorClient().getValidatorConfig();
    assertThat(config.isValidatorExternalSignerSlashingProtectionEnabled()).isTrue();
  }

  @Test
  void shouldDisableSlashingProtectionForExternalSigners() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments(
                "--validators-external-signer-slashing-protection-enabled=false")
            .validatorClient()
            .getValidatorConfig();
    assertThat(config.isValidatorExternalSignerSlashingProtectionEnabled()).isFalse();
  }

  @Test
  void shouldLoadGraffitiFromFile() {
    final ValidatorConfig config =
        getTekuConfigurationFromFile("validatorOptionsWithGraffitiFile_config.yaml")
            .validatorClient()
            .getValidatorConfig();

    assertThat(config.getGraffitiProvider().get()).isEqualTo(Optional.of(graffiti));
  }

  @Test
  void beaconNodeApiEndpoint_shouldBeEmptyByDefault() {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments();
    assertThat(tekuConfiguration.validatorClient().getValidatorConfig().getBeaconNodeApiEndpoint())
        .isEmpty();
  }

  @Test
  void shouldReadMultipleValidUrls() throws URISyntaxException {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments(
                "--Xvalidators-publish-to-additional-nodes=http://u:h@t.net:9090,https://u:p@u.com")
            .validatorClient()
            .getValidatorConfig();
    assertThat(config.getAdditionalPublishUrls())
        .containsExactlyInAnyOrder(new URI("http://u:h@t.net:9090"), new URI("https://u:p@u.com"));
  }

  @Test
  void shouldDetectInvalidPublishUrl() {
    assertThatThrownBy(
            () ->
                getTekuConfigurationFromArguments(
                    "--Xvalidators-publish-to-additional-nodes=invalid-{url}"))
        .hasMessageContaining("cannot convert 'invalid-{url}' to URI");
  }

  @Test
  public void shouldReportEmptyIfFeeRecipientNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.validatorClient().getValidatorConfig().getSuggestedFeeRecipient()).isEmpty();
  }

  @Test
  public void shouldReportAddressIfFeeRecipientSpecified() {
    final String[] args = {
      "--Xvalidators-suggested-fee-recipient-address", "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.validatorClient().getValidatorConfig().getSuggestedFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73")));
  }
}
