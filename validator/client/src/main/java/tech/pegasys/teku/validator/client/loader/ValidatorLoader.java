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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import java.io.File;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;

public class ValidatorLoader {

  private final List<ValidatorSource> validatorSources;
  private final OwnedValidators ownedValidators = new OwnedValidators();
  private final GraffitiProvider graffitiProvider;

  private ValidatorLoader(
      final List<ValidatorSource> validatorSources, final GraffitiProvider graffitiProvider) {
    this.validatorSources = validatorSources;
    this.graffitiProvider = graffitiProvider;
  }

  public static ValidatorLoader create(
      final Spec spec,
      final ValidatorConfig config,
      final InteropConfig interopConfig,
      final SlashingProtector slashingProtector,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Optional<DataDirLayout> maybeDataDir) {
    final Supplier<HttpClient> externalSignerHttpClientFactory =
        Suppliers.memoize(new HttpClientExternalSignerFactory(config)::get);
    return create(
        spec,
        config,
        interopConfig,
        externalSignerHttpClientFactory,
        slashingProtector,
        publicKeyLoader,
        asyncRunner,
        metricsSystem,
        maybeDataDir);
  }

  @VisibleForTesting
  static ValidatorLoader create(
      final Spec spec,
      final ValidatorConfig config,
      final InteropConfig interopConfig,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final SlashingProtector slashingProtector,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Optional<DataDirLayout> maybeDataDir) {
    final List<ValidatorSource> validatorSources = new ArrayList<>();
    if (interopConfig.isInteropEnabled()) {
      validatorSources.add(
          slashingProtected(
              new MockStartValidatorSource(spec, interopConfig, asyncRunner), slashingProtector));
    } else {
      addExternalValidatorSource(
          spec,
          config,
          externalSignerHttpClientFactory,
          slashingProtector,
          publicKeyLoader,
          asyncRunner,
          metricsSystem,
          validatorSources);
      addLocalValidatorSource(spec, config, slashingProtector, asyncRunner, validatorSources);
      if (maybeDataDir.isPresent()) {
        addMutableValidatorSource(
            spec, config, slashingProtector, asyncRunner, validatorSources, maybeDataDir.get());
      }
    }

    return new ValidatorLoader(validatorSources, config.getGraffitiProvider());
  }

  private static void addMutableValidatorSource(
      final Spec spec,
      final ValidatorConfig config,
      final SlashingProtector slashingProtector,
      final AsyncRunner asyncRunner,
      final List<ValidatorSource> validatorSources,
      final DataDirLayout dataDirLayout) {
    final Path keystorePath = ValidatorClientService.getAlterableKeystorePath(dataDirLayout);
    final Path keystorePasswordPath =
        ValidatorClientService.getAlterableKeystorePasswordPath(dataDirLayout);
    if (Files.exists(keystorePath) && Files.exists(keystorePasswordPath)) {
      final KeyStoreFilesLocator keyStoreFilesLocator =
          new KeyStoreFilesLocator(
              List.of(keystorePath + File.pathSeparator + keystorePasswordPath),
              File.pathSeparator);
      validatorSources.add(
          slashingProtected(
              new LocalValidatorSource(
                  spec,
                  config.isValidatorKeystoreLockingEnabled(),
                  new KeystoreLocker(),
                  keyStoreFilesLocator,
                  asyncRunner,
                  false),
              slashingProtector));
    }
  }

  private static void addLocalValidatorSource(
      final Spec spec,
      final ValidatorConfig config,
      final SlashingProtector slashingProtector,
      final AsyncRunner asyncRunner,
      final List<ValidatorSource> validatorSources) {
    if (config.getValidatorKeys() != null) {
      KeyStoreFilesLocator keyStoreFilesLocator =
          new KeyStoreFilesLocator(config.getValidatorKeys(), File.pathSeparator);
      validatorSources.add(
          slashingProtected(
              new LocalValidatorSource(
                  spec,
                  config.isValidatorKeystoreLockingEnabled(),
                  new KeystoreLocker(),
                  keyStoreFilesLocator,
                  asyncRunner,
                  true),
              slashingProtector));
    }
  }

  private static void addExternalValidatorSource(
      final Spec spec,
      final ValidatorConfig config,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final SlashingProtector slashingProtector,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final List<ValidatorSource> validatorSources) {
    if (!config.getValidatorExternalSignerPublicKeySources().isEmpty()) {
      final ValidatorSource externalValidatorSource =
          ExternalValidatorSource.create(
              spec,
              metricsSystem,
              config,
              externalSignerHttpClientFactory,
              publicKeyLoader,
              asyncRunner);
      validatorSources.add(
          config.isValidatorExternalSignerSlashingProtectionEnabled()
              ? slashingProtected(externalValidatorSource, slashingProtector)
              : externalValidatorSource);
    }
  }

  // synchronized to ensure that only one load is active at a time
  public synchronized void loadValidators() {
    final Map<BLSPublicKey, ValidatorProvider> validatorProviders = new HashMap<>();
    validatorSources.forEach(source -> addValidatorsFromSource(validatorProviders, source));
    MultithreadedValidatorLoader.loadValidators(
        ownedValidators, validatorProviders, graffitiProvider);
  }

  public OwnedValidators getOwnedValidators() {
    return ownedValidators;
  }

  private void addValidatorsFromSource(
      final Map<BLSPublicKey, ValidatorProvider> validators, final ValidatorSource source) {
    source.getAvailableValidators().stream()
        .filter(provider -> !ownedValidators.hasValidator(provider.getPublicKey()))
        .forEach(
            validatorProvider ->
                validators.putIfAbsent(validatorProvider.getPublicKey(), validatorProvider));
  }

  private static ValidatorSource slashingProtected(
      final ValidatorSource validatorSource, final SlashingProtector slashingProtector) {
    return new SlashingProtectedValidatorSource(validatorSource, slashingProtector);
  }
}
