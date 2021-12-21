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

package tech.pegasys.teku.validator.api;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

class ValidatorConfigTest {

  private final ValidatorConfig.Builder configBuilder = ValidatorConfig.builder();

  @Test
  public void shouldThrowExceptionIfExternalPublicKeysAreSpecifiedWithoutExternalSignerUrl() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerPublicKeySources(
            List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-url' and '--validators-external-signer-public-keys' must be specified together");
  }

  @Test
  public void noExceptionThrownIfExternalSignerUrlIsSpecifiedWithoutExternalPublicKeys()
      throws MalformedURLException {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL());
    Assertions.assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  public void noExceptionThrownIfBothExternalSignerUrlAndPublicKeysAreSpecified()
      throws MalformedURLException {
    final ValidatorConfig.Builder builder =
        configBuilder
            .validatorExternalSignerPublicKeySources(
                List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()))
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL());

    Assertions.assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  public void shouldThrowExceptionIfExternalSignerKeystoreSpecifiedWithoutPasswordFile() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerKeystore(Path.of("somepath"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-keystore' and '--validators-external-signer-keystore-password-file' must be specified together");
  }

  @Test
  public void shouldThrowExceptionIfExternalSignerKeystorePasswordFileIsSpecifiedWithoutKeystore() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerKeystorePasswordFile(Path.of("somepath"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-keystore' and '--validators-external-signer-keystore-password-file' must be specified together");
  }

  @Test
  public void noExceptionThrownIfBothExternalSignerKeystoreAndPasswordFileAreSpecified() {
    final ValidatorConfig.Builder builder =
        configBuilder
            .validatorExternalSignerKeystore(Path.of("somepath"))
            .validatorExternalSignerKeystorePasswordFile(Path.of("somepath"));

    Assertions.assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  public void shouldThrowExceptionIfExternalSignerTruststoreSpecifiedWithoutPasswordFile() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerTruststore(Path.of("somepath"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-truststore' and '--validators-external-signer-truststore-password-file' must be specified together");
  }

  @Test
  public void
      shouldThrowExceptionIfExternalSignerTruststorePasswordFileIsSpecifiedWithoutTruststore() {
    final ValidatorConfig.Builder builder =
        configBuilder.validatorExternalSignerTruststorePasswordFile(Path.of("somepath"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(builder::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-external-signer-truststore' and '--validators-external-signer-truststore-password-file' must be specified together");
  }

  @Test
  public void noExceptionThrownIfBothExternalSignerTruststoreAndPasswordFileAreSpecified() {
    final ValidatorConfig.Builder builder =
        configBuilder
            .validatorExternalSignerTruststore(Path.of("somepath"))
            .validatorExternalSignerTruststorePasswordFile(Path.of("somepath"));

    Assertions.assertThatCode(builder::build).doesNotThrowAnyException();
  }

  @Test
  public void merge_shouldThrowExceptionIfExternalSignerPublicKeySourcesIsSpecified()
      throws MalformedURLException {
    final ValidatorConfig config =
        configBuilder
            .validatorExternalSignerPublicKeySources(
                List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()))
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL())
            .build();

    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(config::getSuggestedFeeRecipient)
        .withMessageContaining(
            "Invalid configuration. --Xvalidators-suggested-fee-recipient-address must be specified when Merge milestone is active");
  }

  @Test
  public void merge_shouldThrowExceptionIfValidatorKeysAreSpecified() throws MalformedURLException {
    final ValidatorConfig config = configBuilder.validatorKeys(List.of("some string")).build();

    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(config::getSuggestedFeeRecipient)
        .withMessageContaining(
            "Invalid configuration. --Xvalidators-suggested-fee-recipient-address must be specified when Merge milestone is active");
  }

  @Test
  public void merge_noExceptionThrownIfIfExternalSignerPublicKeySourcesIsSpecified()
      throws MalformedURLException {
    final ValidatorConfig config =
        configBuilder
            .validatorExternalSignerPublicKeySources(
                List.of(BLSTestUtil.randomKeyPair(0).getPublicKey().toString()))
            .validatorExternalSignerUrl(URI.create("http://localhost:9000").toURL())
            .suggestedFeeRecipient("0x0000000000000000000000000000000000000000")
            .build();

    Assertions.assertThatCode(config::getSuggestedFeeRecipient).doesNotThrowAnyException();
  }

  @Test
  public void merge_noExceptionThrownIfIfValidatorKeysAreSpecified() {
    final ValidatorConfig config =
        configBuilder
            .validatorKeys(List.of("some string"))
            .suggestedFeeRecipient(
                Eth1Address.fromHexString("0x0000000000000000000000000000000000000000"))
            .build();

    Assertions.assertThatCode(config::getSuggestedFeeRecipient).doesNotThrowAnyException();
  }
}
