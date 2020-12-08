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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import java.net.http.HttpClient;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.SlashingProtectedSigner;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.signer.ExternalSigner;
import tech.pegasys.teku.validator.client.signer.ExternalSignerStatusLogger;
import tech.pegasys.teku.validator.client.signer.ExternalSignerUpcheck;

public class ValidatorLoader {

  private final SlashingProtector slashingProtector;
  private final AsyncRunner asyncRunner;

  private ValidatorLoader(
      final SlashingProtector slashingProtector, final AsyncRunner asyncRunner) {
    this.slashingProtector = slashingProtector;
    this.asyncRunner = asyncRunner;
  }

  public static ValidatorLoader create(
      final SlashingProtector slashingProtector, final AsyncRunner asyncRunner) {
    return new ValidatorLoader(slashingProtector, asyncRunner);
  }

  public Map<BLSPublicKey, Validator> initializeValidators(
      final ValidatorConfig config, final GlobalConfiguration globalConfiguration) {
    final Supplier<HttpClient> externalSignerHttpClientFactory =
        Suppliers.memoize(new HttpClientExternalSignerFactory(config)::get);
    return initializeValidators(config, globalConfiguration, externalSignerHttpClientFactory);
  }

  @VisibleForTesting
  Map<BLSPublicKey, Validator> initializeValidators(
      final ValidatorConfig config,
      final GlobalConfiguration globalConfiguration,
      final Supplier<HttpClient> externalSignerHttpClientFactory) {
    // Get validator connection info and create a new Validator object and put it into the
    // Validators map

    final Map<BLSPublicKey, Validator> validators = new HashMap<>();
    validators.putAll(createLocalSignerValidator(config, globalConfiguration));
    validators.putAll(createExternalSignerValidator(config, externalSignerHttpClientFactory));

    STATUS_LOG.validatorsInitialised(
        validators.values().stream()
            .map(Validator::getPublicKey)
            .map(BLSPublicKey::toAbbreviatedString)
            .collect(Collectors.toList()));
    return validators;
  }

  private Map<BLSPublicKey, Validator> createLocalSignerValidator(
      final ValidatorConfig config, final GlobalConfiguration globalConfiguration) {
    return loadValidatorKeys(config, globalConfiguration).stream()
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
      final ValidatorConfig config, final Supplier<HttpClient> externalSignerHttpClientFactory) {
    if (config.getValidatorExternalSignerPublicKeys().isEmpty()) {
      return Collections.emptyMap();
    }

    setupExternalSignerStatusLogging(config, externalSignerHttpClientFactory);

    return config.getValidatorExternalSignerPublicKeys().stream()
        .map(
            publicKey -> {
              final ExternalSigner externalSigner =
                  new ExternalSigner(
                      externalSignerHttpClientFactory.get(),
                      config.getValidatorExternalSignerUrl(),
                      publicKey,
                      config.getValidatorExternalSignerTimeout());
              final Signer signer =
                  config.isValidatorExternalSignerSlashingProtectionEnabled()
                      ? createSlashingProtectedSigner(publicKey, externalSigner)
                      : externalSigner;
              return new Validator(publicKey, signer, Optional.ofNullable(config.getGraffiti()));
            })
        .collect(toMap(Validator::getPublicKey, Function.identity()));
  }

  private void setupExternalSignerStatusLogging(
      final ValidatorConfig config, final Supplier<HttpClient> externalSignerHttpClientFactory) {
    final ExternalSignerUpcheck externalSignerUpcheck =
        new ExternalSignerUpcheck(
            externalSignerHttpClientFactory.get(),
            config.getValidatorExternalSignerUrl(),
            config.getValidatorExternalSignerTimeout());
    final ExternalSignerStatusLogger externalSignerStatusLogger =
        new ExternalSignerStatusLogger(
            STATUS_LOG,
            externalSignerUpcheck::upcheck,
            config.getValidatorExternalSignerUrl(),
            asyncRunner);
    // initial status log
    externalSignerStatusLogger.log();
    // recurring status log
    externalSignerStatusLogger.logWithFixedDelay();
  }

  private Signer createSlashingProtectedSigner(final BLSPublicKey publicKey, final Signer signer) {
    return new SlashingProtectedSigner(publicKey, slashingProtector, signer);
  }

  private static Collection<BLSKeyPair> loadValidatorKeys(
      final ValidatorConfig config, final GlobalConfiguration globalConfiguration) {
    final Set<ValidatorKeyProvider> keyProviders = new LinkedHashSet<>();
    if (globalConfiguration.isInteropEnabled()) {
      keyProviders.add(new MockStartValidatorKeyProvider(globalConfiguration));
    } else {
      // support loading keys both from encrypted keystores
      if (config.getValidatorKeystorePasswordFilePairs() != null) {
        keyProviders.add(new KeystoresValidatorKeyProvider(new KeystoreLocker(), config));
      }
    }
    return keyProviders.stream()
        .flatMap(validatorKeyProvider -> validatorKeyProvider.loadValidatorKeys().stream())
        .collect(Collectors.toSet());
  }
}
