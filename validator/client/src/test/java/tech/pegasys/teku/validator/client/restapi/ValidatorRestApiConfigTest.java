/*
 * Copyright 2022 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidatorRestApiConfigTest {

  @Test
  void validatorRestApiCanBeDisabled() {
    final ValidatorRestApiConfig config = ValidatorRestApiConfig.builder().build();
    assertThat(config).isInstanceOf(ValidatorRestApiConfig.class);
    assertThat(config.isRestApiEnabled()).isFalse();
  }

  @Test
  void validatorApiRequiresSsl() {
    assertThatThrownBy(() -> ValidatorRestApiConfig.builder().restApiEnabled(true).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requires ssl keystore");
  }

  @Test
  void validatorApiRequiresKeystoreToExist(@TempDir final Path tempPath) {
    assertThatThrownBy(
            () ->
                ValidatorRestApiConfig.builder()
                    .restApiEnabled(true)
                    .validatorApiKeystoreFile(tempPath.resolve("keystore").toString())
                    .validatorApiKeystorePasswordFile(tempPath.resolve("pass").toString())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Could not access Validator api keystore");
  }

  @Test
  void validatorApiDoesNotRequiresPasswordToExist(@TempDir final Path tempPath) throws IOException {
    tempPath.resolve("keystore").toFile().createNewFile();
    final ValidatorRestApiConfig config =
        ValidatorRestApiConfig.builder()
            .restApiEnabled(true)
            .validatorApiKeystoreFile(tempPath.resolve("keystore").toString())
            .build();
    assertThat(config).isInstanceOf(ValidatorRestApiConfig.class);
    assertThat(config.isRestApiEnabled()).isTrue();
  }
}
