/*
 * Copyright ConsenSys Software Inc., 2022
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
import java.net.URL;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes48;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.signatures.DeletableSigner;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.loader.ValidatorSource.ValidatorProvider;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class ValidatorLoader {

  private static final Logger LOG = LogManager.getLogger();
  private final List<ValidatorSource> validatorSources;
  private final Optional<ValidatorSource> mutableLocalValidatorSource;
  private final Optional<ValidatorSource> mutableExternalValidatorSource;
  private final OwnedValidators ownedValidators = new OwnedValidators();
  private final GraffitiProvider graffitiProvider;
  private final Optional<DataDirLayout> maybeDataDirLayout;
  private final SlashingProtectionLogger slashingProtectionLogger;

  private ValidatorLoader(
      final List<ValidatorSource> validatorSources,
      final Optional<ValidatorSource> mutableLocalValidatorSource,
      final Optional<ValidatorSource> mutableExternalValidatorSource,
      final GraffitiProvider graffitiProvider,
      final Optional<DataDirLayout> maybeDataDirLayout,
      final SlashingProtectionLogger slashingProtectionLogger) {
    this.validatorSources = validatorSources;
    this.mutableLocalValidatorSource = mutableLocalValidatorSource;
    this.mutableExternalValidatorSource = mutableExternalValidatorSource;
    this.graffitiProvider = graffitiProvider;
    this.maybeDataDirLayout = maybeDataDirLayout;
    this.slashingProtectionLogger = slashingProtectionLogger;
  }

  public static ValidatorLoader create(
      final Spec spec,
      final ValidatorConfig config,
      final InteropConfig interopConfig,
      final SlashingProtector slashingProtector,
      final SlashingProtectionLogger slashingProtectorLogger,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Optional<DataDirLayout> maybeMutableDir) {
    final Supplier<HttpClient> externalSignerHttpClientFactory =
        Suppliers.memoize(new HttpClientExternalSignerFactory(config)::get);
    return create(
        spec,
        config,
        interopConfig,
        externalSignerHttpClientFactory,
        slashingProtector,
        slashingProtectorLogger,
        publicKeyLoader,
        asyncRunner,
        metricsSystem,
        maybeMutableDir);
  }

  // synchronized to ensure that only one load is active at a time
  public synchronized void loadValidators() {
    final Map<BLSPublicKey, ValidatorProvider> validatorProviders = new HashMap<>();
    validatorSources.forEach(source -> addValidatorsFromSource(validatorProviders, source));
    MultithreadedValidatorLoader.loadValidators(
        ownedValidators, validatorProviders, graffitiProvider);
    slashingProtectionLogger.protectionSummary(ownedValidators.getActiveValidators());
  }

  public DeleteKeyResult deleteLocalMutableValidator(final BLSPublicKey publicKey) {
    if (mutableLocalValidatorSource.isEmpty()) {
      return DeleteKeyResult.error(
          "Unable to delete validator, could not determine the storage location.");
    }
    return mutableLocalValidatorSource.get().deleteValidator(publicKey);
  }

  public synchronized PostKeyResult loadLocalMutableValidator(
      final KeyStoreData keyStoreData,
      final String password,
      final Optional<SlashingProtectionImporter> slashingProtectionImporter) {
    if (!canAddValidator(mutableLocalValidatorSource)) {
      return PostKeyResult.error("Not able to add validator");
    }
    final BLSPublicKey publicKey =
        BLSPublicKey.fromBytesCompressed(Bytes48.wrap(keyStoreData.getPubkey()));

    if (slashingProtectionImporter.isPresent()) {
      final Optional<String> errorString =
          slashingProtectionImporter.get().updateSigningRecord(publicKey, LOG::debug);
      if (errorString.isPresent()) {
        return PostKeyResult.error(errorString.get());
      }
    }
    if (ownedValidators.hasValidator(publicKey)) {
      return PostKeyResult.duplicate();
    }

    final AddValidatorResult validatorAddResult =
        mutableLocalValidatorSource.get().addValidator(keyStoreData, password, publicKey);

    if (validatorAddResult.getSigner().isEmpty()) {
      return validatorAddResult.getResult();
    }
    addValidator(validatorAddResult.getSigner().get(), publicKey);
    return PostKeyResult.success();
  }

  public DeleteKeyResult deleteExternalMutableValidator(final BLSPublicKey publicKey) {
    if (mutableExternalValidatorSource.isEmpty()) {
      return DeleteKeyResult.error(
          "Unable to delete external validator, could not determine the storage location.");
    }
    return mutableExternalValidatorSource.get().deleteValidator(publicKey);
  }

  public synchronized PostKeyResult loadExternalMutableValidator(
      final BLSPublicKey publicKey, final Optional<URL> signerUrl) {
    if (!canAddValidator(mutableExternalValidatorSource)) {
      return PostKeyResult.error("Not able to add validator");
    }
    if (ownedValidators.hasValidator(publicKey)) {
      return PostKeyResult.duplicate();
    }

    final AddValidatorResult validatorAddResult =
        mutableExternalValidatorSource.get().addValidator(publicKey, signerUrl);

    if (validatorAddResult.getSigner().isEmpty()) {
      return validatorAddResult.getResult();
    }
    addValidator(validatorAddResult.getSigner().get(), publicKey);
    return PostKeyResult.success();
  }

  private void addValidator(final Signer signer, final BLSPublicKey publicKey) {
    ownedValidators.addValidator(
        new Validator(publicKey, new DeletableSigner(signer), graffitiProvider, false));

    LOG.info("Added validator: {}", publicKey.toString());
  }

  private boolean canAddValidator(Optional<ValidatorSource> validatorSource) {
    return validatorSource.isPresent()
        && validatorSource.get().canUpdateValidators()
        && maybeDataDirLayout.isPresent();
  }

  public OwnedValidators getOwnedValidators() {
    return ownedValidators;
  }

  @VisibleForTesting
  static ValidatorLoader create(
      final Spec spec,
      final ValidatorConfig config,
      final InteropConfig interopConfig,
      final Supplier<HttpClient> externalSignerHttpClientFactory,
      final SlashingProtector slashingProtector,
      final SlashingProtectionLogger slashingProtectionLogger,
      final PublicKeyLoader publicKeyLoader,
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final Optional<DataDirLayout> maybeMutableDir) {
    final ValidatorSourceFactory validatorSources =
        new ValidatorSourceFactory(
            spec,
            config,
            interopConfig,
            externalSignerHttpClientFactory,
            slashingProtector,
            publicKeyLoader,
            asyncRunner,
            metricsSystem,
            maybeMutableDir);

    final List<ValidatorSource> validatorSourceList = validatorSources.createValidatorSources();
    return new ValidatorLoader(
        validatorSourceList,
        validatorSources.getMutableLocalValidatorSource(),
        validatorSources.getMutableExternalValidatorSource(),
        config.getGraffitiProvider(),
        maybeMutableDir,
        slashingProtectionLogger);
  }

  @VisibleForTesting
  static ValidatorLoader create(
      final List<ValidatorSource> validatorSources,
      final Optional<ValidatorSource> mutableLocalValidatorSource,
      final Optional<ValidatorSource> mutableExternalValidatorSource,
      final GraffitiProvider graffitiProvider,
      final Optional<DataDirLayout> maybeDataDirLayout,
      final SlashingProtectionLogger slashingProtectionLogger) {
    return new ValidatorLoader(
        validatorSources,
        mutableLocalValidatorSource,
        mutableExternalValidatorSource,
        graffitiProvider,
        maybeDataDirLayout,
        slashingProtectionLogger);
  }

  private void addValidatorsFromSource(
      final Map<BLSPublicKey, ValidatorProvider> validators, final ValidatorSource source) {
    source.getAvailableValidators().stream()
        .filter(provider -> !ownedValidators.hasValidator(provider.getPublicKey()))
        .forEach(
            validatorProvider ->
                validators.putIfAbsent(validatorProvider.getPublicKey(), validatorProvider));
  }

  public SlashingProtectionLogger getSlashingProtectionLogger() {
    return slashingProtectionLogger;
  }
}
