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

package tech.pegasys.artemis.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.validator.client.ExternalMessageSignerService;
import tech.pegasys.artemis.validator.client.LocalMessageSignerService;

class ValidatorLoaderTest {

  private static final String PUBLIC_KEY =
      "0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c";
  private static final String VALIDATOR_KEY_FILE =
      "- {privkey: '0x25295f0d1d592a90b333e26e85149708208e9f8e8bc18f6c77bd62f8ad7a6866',\n"
          + "  pubkey: '0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c'}";

  @Test
  void initializeValidatorsWithExternalMessageSignerWhenConfigHasExternalSigningPublicKeys() {
    final String config =
        "validator.externalSignerUrl=\"http://localhost:9000\"\n"
            + "validator.externalSignerPublicKeys=[\""
            + PUBLIC_KEY
            + "\"]";
    final ArtemisConfiguration artemisConfiguration = ArtemisConfiguration.fromString(config);
    final Map<BLSPublicKey, ValidatorInfo> blsPublicKeyValidatorInfoMap =
        ValidatorLoader.initializeValidators(artemisConfiguration);

    assertThat(blsPublicKeyValidatorInfoMap).isNotEmpty();
    final BLSPublicKey key = BLSPublicKey.fromBytes(Bytes.fromHexString(PUBLIC_KEY));
    assertThat(blsPublicKeyValidatorInfoMap.get(key)).isNotNull();
    assertThat(blsPublicKeyValidatorInfoMap.get(key).getSignerService())
        .isInstanceOf(ExternalMessageSignerService.class);
  }

  @Test
  void initializeValidatorsWithLocalMessageSignerWhenConfigHasValidatorsKeyFile(
      @TempDir Path tempDir) throws IOException {
    final Path validatorKeyFile = tempDir.resolve("validatorKeyFile");
    Files.writeString(validatorKeyFile, VALIDATOR_KEY_FILE);
    final String config =
        "validator.validatorsKeyFile=\"" + validatorKeyFile.toAbsolutePath() + "\"";

    final ArtemisConfiguration artemisConfiguration = ArtemisConfiguration.fromString(config);
    final Map<BLSPublicKey, ValidatorInfo> blsPublicKeyValidatorInfoMap =
        ValidatorLoader.initializeValidators(artemisConfiguration);

    assertThat(blsPublicKeyValidatorInfoMap).isNotEmpty();
    final BLSPublicKey key = BLSPublicKey.fromBytes(Bytes.fromHexString(PUBLIC_KEY));
    assertThat(blsPublicKeyValidatorInfoMap.get(key)).isNotNull();
    assertThat(blsPublicKeyValidatorInfoMap.get(key).getSignerService())
        .isInstanceOf(LocalMessageSignerService.class);
  }
}
