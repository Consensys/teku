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

package tech.pegasys.teku.validator.client.loader;

import static java.util.stream.Collectors.toMap;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.SlashingProtectedSigner;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.signer.ExternalSigner;

public class ValidatorLoader {

  private final SlashingProtector slashingProtector;
  private final AsyncRunner asyncRunner;

  public ValidatorLoader(final SlashingProtector slashingProtector, final AsyncRunner asyncRunner) {
    this.slashingProtector = slashingProtector;
    this.asyncRunner = asyncRunner;
  }

  public Map<BLSPublicKey, Validator> initializeValidators(GlobalConfiguration config) {
    // Get validator connection info and create a new Validator object and put it into the
    // Validators map

    final Map<BLSPublicKey, Validator> validators = new HashMap<>();
    validators.putAll(createLocalSignerValidator(config));
    validators.putAll(createExternalSignerValidator(config));

    STATUS_LOG.validatorsInitialised(
        validators.values().stream()
            .map(Validator::getPublicKey)
            .map(BLSPublicKey::toAbbreviatedString)
            .collect(Collectors.toList()));
    return validators;
  }

  private Map<BLSPublicKey, Validator> createLocalSignerValidator(
      final GlobalConfiguration config) {
    return loadValidatorKeys(config).stream()
        .map(
            blsKeyPair ->
                new Validator(
                    blsKeyPair.getPublicKey(),
                    createSlashingProtectedSigner(
                        blsKeyPair.getPublicKey(), new LocalSigner(blsKeyPair, asyncRunner)),
                    Optional.ofNullable(config.getGraffiti())))
        .collect(toMap(Validator::getPublicKey, Function.identity()));
  }

  private Map<BLSPublicKey, Validator> createExternalSignerValidator(
      final GlobalConfiguration config) {
    final Duration timeout = Duration.ofMillis(config.getValidatorExternalSignerTimeout());
    return config.getValidatorExternalSignerPublicKeys().stream()
        .map(
            publicKey ->
                new Validator(
                    publicKey,
                    createSlashingProtectedSigner(
                        publicKey,
                        new ExternalSigner(
                            config.getValidatorExternalSignerUrl(), publicKey, timeout)),
                    Optional.ofNullable(config.getGraffiti())))
        .collect(toMap(Validator::getPublicKey, Function.identity()));
  }

  private Signer createSlashingProtectedSigner(final BLSPublicKey publicKey, final Signer signer) {
    return new SlashingProtectedSigner(publicKey, slashingProtector, signer);
  }

  private static Collection<BLSKeyPair> loadValidatorKeys(final GlobalConfiguration config) {
    final Set<ValidatorKeyProvider> keyProviders = new LinkedHashSet<>();
    if (config.isInteropEnabled()) {
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
