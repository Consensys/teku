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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.validator.client.ExternalMessageSignerService;
import tech.pegasys.artemis.validator.client.LocalMessageSignerService;
import tech.pegasys.artemis.validator.client.Signer;
import tech.pegasys.artemis.validator.client.Validator;

public class ValidatorLoader {

  private static final Logger LOG = LogManager.getLogger();

  public static List<Validator> initializeValidators(ArtemisConfiguration config) {
    // Get validator connection info and create a new Validator object and put it into the
    // Validators map

    final List<Validator> validators = new ArrayList<>();
    validators.addAll(createLocalSignerValidator(config));
    validators.addAll(createExternalSignerValidator(config));

    LOG.debug(
        "Loaded validators: {}",
        () ->
            validators.stream()
                .map(Validator::getPublicKey)
                .map(BLSPublicKey::toString)
                .collect(joining(", ")));
    return validators;
  }

  private static List<Validator> createLocalSignerValidator(final ArtemisConfiguration config) {
    return loadValidatorKeys(config).stream()
        .map(
            blsKeyPair ->
                new Validator(
                    blsKeyPair.getPublicKey(),
                    new Signer(new LocalMessageSignerService(blsKeyPair))))
        .collect(Collectors.toList());
  }

  private static List<Validator> createExternalSignerValidator(final ArtemisConfiguration config) {
    final Duration timeout = Duration.ofMillis(config.getValidatorExternalSignerTimeout());
    return config.getValidatorExternalSignerPublicKeys().stream()
        .map(
            publicKey ->
                new Validator(
                    publicKey,
                    new Signer(
                        new ExternalMessageSignerService(
                            config.getValidatorExternalSignerUrl(), publicKey, timeout))))
        .collect(Collectors.toList());
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
