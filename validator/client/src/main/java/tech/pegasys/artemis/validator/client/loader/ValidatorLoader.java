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

package tech.pegasys.artemis.validator.client.loader;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.core.Signer;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.validator.client.Validator;
import tech.pegasys.artemis.validator.client.signer.ExternalMessageSignerService;
import tech.pegasys.artemis.validator.client.signer.LocalMessageSignerService;

public class ValidatorLoader {

  private static final Logger LOG = LogManager.getLogger();

  public static Map<BLSPublicKey, Validator> initializeValidators(ArtemisConfiguration config) {
    // Get validator connection info and create a new Validator object and put it into the
    // Validators map

    final Map<BLSPublicKey, Validator> validators = new HashMap<>();
    validators.putAll(createLocalSignerValidator(config));
    validators.putAll(createExternalSignerValidator(config));

    LOG.debug(
        "Loaded validators: {}",
        () ->
            validators.values().stream()
                .map(Validator::getPublicKey)
                .map(BLSPublicKey::toString)
                .collect(joining(", ")));
    return validators;
  }

  private static Map<BLSPublicKey, Validator> createLocalSignerValidator(
      final ArtemisConfiguration config) {
    return loadValidatorKeys(config).stream()
        .map(
            blsKeyPair ->
                new Validator(
                    blsKeyPair.getPublicKey(),
                    new Signer(new LocalMessageSignerService(blsKeyPair))))
        .collect(toMap(Validator::getPublicKey, Function.identity()));
  }

  private static Map<BLSPublicKey, Validator> createExternalSignerValidator(
      final ArtemisConfiguration config) {
    final Duration timeout = Duration.ofMillis(config.getValidatorExternalSignerTimeout());
    return config.getValidatorExternalSignerPublicKeys().stream()
        .map(
            publicKey ->
                new Validator(
                    publicKey,
                    new Signer(
                        new ExternalMessageSignerService(
                            config.getValidatorExternalSignerUrl(), publicKey, timeout))))
        .collect(toMap(Validator::getPublicKey, Function.identity()));
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
