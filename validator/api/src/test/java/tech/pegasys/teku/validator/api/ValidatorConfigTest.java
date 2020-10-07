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

import static java.util.Collections.emptyList;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.util.config.InvalidConfigurationException;

class ValidatorConfigTest {

  private final ValidatorConfig.Builder configBuilder = ValidatorConfig.builder();

  @Test
  public void shouldThrowExceptionIfValidatorKeystoreFilesButNotValidatorKeystorePasswordFiles() {
    final ValidatorConfig.Builder config = buildConfig(List.of("foo"), emptyList());
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(config::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-key-files' and '--validators-key-password-files' must be specified together");
  }

  @Test
  public void shouldThrowExceptionIfValidatorKeystorePasswordFilesButNotValidatorKeystoreFiles() {
    final ValidatorConfig.Builder config = buildConfig(emptyList(), List.of("foo"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(config::build)
        .withMessageContaining(
            "Invalid configuration. '--validators-key-files' and '--validators-key-password-files' must be specified together");
  }

  @Test
  public void shouldThrowExceptionIfValidatorKeystoreFilesPasswordsLengthMismatch() {
    final ValidatorConfig.Builder config = buildConfig(List.of("a", "b"), List.of("password"));
    Assertions.assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(config::build)
        .withMessageContaining(
            "Invalid configuration. The number of --validators-key-files (2) must equal the number of --validators-key-password-files (1)");
  }

  private ValidatorConfig.Builder buildConfig(
      final List<String> validatorKeystoreFiles,
      final List<String> validatorKeystorePasswordFiles) {
    return configBuilder
        .validatorKeystoreFiles(validatorKeystoreFiles)
        .validatorKeystorePasswordFiles(validatorKeystorePasswordFiles);
  }
}
