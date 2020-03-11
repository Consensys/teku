/*
 * Copyright 2019 ConsenSys AG.
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

import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.validator.client.LocalMessageSignerService;

class ValidatorLoader {

  private static final Logger LOG = LogManager.getLogger();

  static Map<BLSPublicKey, ValidatorInfo> initializeValidators(ArtemisConfiguration config) {
    // Get validator connection info and create a new ValidatorInfo object and put it into the
    // Validators map
    final Map<BLSPublicKey, ValidatorInfo> validators =
        loadValidatorKeys(config).stream()
            .collect(
                Collectors.toMap(
                    BLSKeyPair::getPublicKey,
                    blsKeyPair -> new ValidatorInfo(new LocalMessageSignerService(blsKeyPair))));

    if (LOG.isDebugEnabled()) {
      Streams.mapWithIndex(
              validators.keySet().stream(),
              (publicKey, index) -> "Validator " + index + ": " + publicKey.toString())
          .forEach(LOG::debug);
    }
    return validators;
  }

  private static Collection<BLSKeyPair> loadValidatorKeys(final ArtemisConfiguration config) {
    final Set<ValidatorKeyProvider> keyProviders = new LinkedHashSet<>();
    if (config.getValidatorsKeyFile() == null
        && config.getValidatorKeystorePasswordFilePairs() == null) {
      keyProviders.add(new MockStartValidatorKeyProvider());
    } else {
      // support loading keys both from unencrypted yaml and encrypted keystores
      if (config.getValidatorsKeyFile() != null) {
        keyProviders.add(new YamlValidatorKeyProvider());
      }

      if (config.getValidatorKeystorePasswordFilePairs() != null) {
        keyProviders.add(new KeystoresValidatorKeyProvider());
      }
    }
    return keyProviders.stream()
        .flatMap(validatorKeyProvider -> validatorKeyProvider.loadValidatorKeys(config).stream())
        .collect(Collectors.toSet());
  }
}
