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

package tech.pegasys.teku.validator.client.loader;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.LocalMessageSignerService;
import tech.pegasys.teku.util.config.TekuConfiguration;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.signer.ExternalMessageSignerService;

class ValidatorLoaderTest {

  private static final String PUBLIC_KEY1 =
      "0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c";
  private static final String PUBLIC_KEY2 =
      "0xb89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b";
  private static final String VALIDATOR_KEY_FILE =
      "- {privkey: '0x25295f0d1d592a90b333e26e85149708208e9f8e8bc18f6c77bd62f8ad7a6866',\n"
          + "  pubkey: '0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c'}";

  @Test
  void initializeValidatorsWithExternalMessageSignerWhenConfigHasExternalSigningPublicKeys() {
    final TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .setValidatorExternalSignerUrl("http://localhost:9000")
            .setValidatorExternalSignerPublicKeys(Collections.singletonList(PUBLIC_KEY1))
            .setValidatorKeystoreFiles(emptyList())
            .setValidatorKeystorePasswordFiles(emptyList())
            .build();
    final Map<BLSPublicKey, Validator> validators =
        ValidatorLoader.initializeValidators(tekuConfiguration);

    assertThat(validators).hasSize(1);
    final BLSPublicKey key = BLSPublicKey.fromBytes(Bytes.fromHexString(PUBLIC_KEY1));
    final Validator validator = validators.get(key);
    assertThat(validator).isNotNull();
    assertThat(validator.getPublicKey()).isEqualTo(key);
    assertThat(validator.getSigner().getMessageSignerService())
        .isInstanceOf(ExternalMessageSignerService.class);
  }

  @Test
  void initializeValidatorsWithLocalMessageSignerWhenConfigHasValidatorsKeyFile(
      @TempDir Path tempDir) throws IOException {
    final Path validatorKeyFile = tempDir.resolve("validatorKeyFile");
    Files.writeString(validatorKeyFile, VALIDATOR_KEY_FILE);

    final TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .setValidatorKeyFile(validatorKeyFile.toAbsolutePath().toString())
            .setValidatorKeystoreFiles(emptyList())
            .setValidatorKeystorePasswordFiles(emptyList())
            .build();
    final Map<BLSPublicKey, Validator> validators =
        ValidatorLoader.initializeValidators(tekuConfiguration);

    assertThat(validators).hasSize(1);
    final BLSPublicKey key = BLSPublicKey.fromBytes(Bytes.fromHexString(PUBLIC_KEY1));
    final Validator validator = validators.get(key);
    assertThat(validator).isNotNull();
    assertThat(validator.getPublicKey()).isEqualTo(key);
    assertThat(validator.getSigner().getMessageSignerService())
        .isInstanceOf(LocalMessageSignerService.class);
  }

  @Test
  void initializeValidatorsWithBothLocalAndExternalSigners(@TempDir Path tempDir)
      throws IOException {
    final Path validatorKeyFile = tempDir.resolve("validatorKeyFile");
    Files.writeString(validatorKeyFile, VALIDATOR_KEY_FILE);

    final TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .setValidatorExternalSignerUrl("http://localhost:9000")
            .setValidatorExternalSignerPublicKeys(Collections.singletonList(PUBLIC_KEY2))
            .setValidatorKeyFile(validatorKeyFile.toAbsolutePath().toString())
            .setValidatorKeystoreFiles(emptyList())
            .setValidatorKeystorePasswordFiles(emptyList())
            .build();
    final Map<BLSPublicKey, Validator> validators =
        ValidatorLoader.initializeValidators(tekuConfiguration);

    assertThat(validators).hasSize(2);

    final BLSPublicKey key1 = BLSPublicKey.fromBytes(Bytes.fromHexString(PUBLIC_KEY1));
    final Validator validator1 = validators.get(key1);
    assertThat(validator1).isNotNull();
    assertThat(validator1.getPublicKey()).isEqualTo(key1);
    assertThat(validator1.getSigner().getMessageSignerService())
        .isInstanceOf(LocalMessageSignerService.class);

    final BLSPublicKey key2 = BLSPublicKey.fromBytes(Bytes.fromHexString(PUBLIC_KEY2));
    final Validator validator2 = validators.get(key2);
    assertThat(validator2).isNotNull();
    assertThat(validator2.getPublicKey()).isEqualTo(key2);
    assertThat(validator2.getSigner().getMessageSignerService())
        .isInstanceOf(ExternalMessageSignerService.class);
  }

  @Test
  void initializeValidatorsWithDuplicateKeysInLocalAndExternalSignersTakesExternalAsPriority(
      @TempDir Path tempDir) throws IOException {
    final Path validatorKeyFile = tempDir.resolve("validatorKeyFile");
    Files.writeString(validatorKeyFile, VALIDATOR_KEY_FILE);

    final TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .setValidatorExternalSignerUrl("http://localhost:9000")
            .setValidatorExternalSignerPublicKeys(Collections.singletonList(PUBLIC_KEY1))
            .setValidatorKeyFile(validatorKeyFile.toAbsolutePath().toString())
            .setValidatorKeystoreFiles(emptyList())
            .setValidatorKeystorePasswordFiles(emptyList())
            .build();
    final Map<BLSPublicKey, Validator> validators =
        ValidatorLoader.initializeValidators(tekuConfiguration);

    // Both local and external validators get loaded.
    assertThat(validators).hasSize(1);

    // Local validators are listed first
    final BLSPublicKey key = BLSPublicKey.fromBytes(Bytes.fromHexString(PUBLIC_KEY1));
    final Validator validator = validators.get(key);
    assertThat(validator).isNotNull();
    assertThat(validator.getPublicKey()).isEqualTo(key);
    assertThat(validator.getSigner().getMessageSignerService())
        .isInstanceOf(ExternalMessageSignerService.class);
  }

  @Test
  void initializeInteropValidatorsWhenInteropIsEnabled() {
    final int ownedValidatorCount = 10;
    final TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .setInteropEnabled(true)
            .setInteropOwnedValidatorCount(ownedValidatorCount)
            .setValidatorKeystoreFiles(emptyList())
            .setValidatorKeystorePasswordFiles(emptyList())
            .build();
    final Map<BLSPublicKey, Validator> validators =
        ValidatorLoader.initializeValidators(tekuConfiguration);

    assertThat(validators).hasSize(ownedValidatorCount);
  }

  @Test
  void doNotInitializeInteropValidatorsWhenInteropIsDisabled() {
    final int ownedValidatorCount = 10;
    final TekuConfiguration tekuConfiguration =
        TekuConfiguration.builder()
            .setInteropEnabled(false)
            .setInteropOwnedValidatorCount(ownedValidatorCount)
            .setValidatorKeystoreFiles(emptyList())
            .setValidatorKeystorePasswordFiles(emptyList())
            .build();
    final Map<BLSPublicKey, Validator> validators =
        ValidatorLoader.initializeValidators(tekuConfiguration);

    assertThat(validators).isEmpty();
  }
}
