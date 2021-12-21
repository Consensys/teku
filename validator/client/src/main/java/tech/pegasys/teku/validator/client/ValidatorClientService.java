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

package tech.pegasys.teku.validator.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.signatures.LocalSlashingProtector;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.io.SystemSignalListener;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.BlockDutyFactory;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationDutyFactory;
import tech.pegasys.teku.validator.client.duties.synccommittee.ChainHeadTracker;
import tech.pegasys.teku.validator.client.duties.synccommittee.SyncCommitteeScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApi;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;
import tech.pegasys.teku.validator.relaypublisher.MultiPublishingBeaconNodeApi;

public class ValidatorClientService extends Service {
  private static final Logger LOG = LogManager.getLogger();
  private final EventChannels eventChannels;
  private final ValidatorLoader validatorLoader;
  private final BeaconNodeApi beaconNodeApi;
  private final Optional<RestApi> validatorRestApi;
  private final ForkProvider forkProvider;
  private final Spec spec;

  private final List<ValidatorTimingChannel> validatorTimingChannels = new ArrayList<>();
  private ValidatorStatusLogger validatorStatusLogger;
  private ValidatorIndexProvider validatorIndexProvider;

  private final SafeFuture<Void> initializationComplete = new SafeFuture<>();

  private final MetricsSystem metricsSystem;

  private ValidatorClientService(
      final EventChannels eventChannels,
      final ValidatorLoader validatorLoader,
      final BeaconNodeApi beaconNodeApi,
      final Optional<RestApi> validatorRestApi,
      final ForkProvider forkProvider,
      final Spec spec,
      final MetricsSystem metricsSystem) {
    this.eventChannels = eventChannels;
    this.validatorLoader = validatorLoader;
    this.beaconNodeApi = beaconNodeApi;
    this.validatorRestApi = validatorRestApi;
    this.forkProvider = forkProvider;
    this.spec = spec;
    this.metricsSystem = metricsSystem;
  }

  public static ValidatorClientService create(
      final ServiceConfig services, final ValidatorClientConfiguration config) {
    final EventChannels eventChannels = services.getEventChannels();
    final AsyncRunner asyncRunner = services.createAsyncRunner("validator");
    final boolean useDependentRoots = config.getValidatorConfig().useDependentRoots();
    final boolean generateEarlyAttestations =
        config.getValidatorConfig().generateEarlyAttestations();
    final BeaconNodeApi beaconNodeApi =
        MultiPublishingBeaconNodeApi.create(
            services,
            asyncRunner,
            config.getValidatorConfig().getBeaconNodeApiEndpoint(),
            config.getSpec(),
            useDependentRoots,
            generateEarlyAttestations,
            config.getValidatorConfig().getAdditionalPublishUrls());

    final ValidatorApiChannel validatorApiChannel = beaconNodeApi.getValidatorApi();
    final GenesisDataProvider genesisDataProvider =
        new GenesisDataProvider(asyncRunner, validatorApiChannel);
    final ForkProvider forkProvider = new ForkProvider(config.getSpec(), genesisDataProvider);

    final ValidatorLoader validatorLoader = createValidatorLoader(config, asyncRunner, services);

    final ValidatorRestApiConfig validatorApiConfig = config.getValidatorRestApiConfig();
    Optional<RestApi> validatorRestApi = Optional.empty();
    if (validatorApiConfig.isRestApiEnabled()) {
      validatorRestApi =
          Optional.of(
              ValidatorRestApi.create(
                  validatorApiConfig,
                  new KeyManager(validatorLoader, services.getDataDirLayout())));
    } else {
      LOG.info("validator-api-enabled is false, not starting rest api.");
    }
    ValidatorClientService validatorClientService =
        new ValidatorClientService(
            eventChannels,
            validatorLoader,
            beaconNodeApi,
            validatorRestApi,
            forkProvider,
            config.getSpec(),
            services.getMetricsSystem());

    asyncRunner
        .runAsync(
            () ->
                validatorClientService.initializeValidators(
                    config, validatorApiChannel, asyncRunner))
        .propagateTo(validatorClientService.initializationComplete);
    return validatorClientService;
  }

  private static ValidatorLoader createValidatorLoader(
      final ValidatorClientConfiguration config,
      final AsyncRunner asyncRunner,
      final ServiceConfig services) {
    final Path slashingProtectionPath = getSlashingProtectionPath(services.getDataDirLayout());
    final SlashingProtector slashingProtector =
        new LocalSlashingProtector(
            SyncDataAccessor.create(slashingProtectionPath), slashingProtectionPath);
    return ValidatorLoader.create(
        config.getSpec(),
        config.getValidatorConfig(),
        config.getInteropConfig(),
        slashingProtector,
        new PublicKeyLoader(),
        asyncRunner,
        services.getMetricsSystem(),
        config.getValidatorRestApiConfig().isRestApiEnabled()
            ? Optional.of(services.getDataDirLayout())
            : Optional.empty());
  }

  private void initializeValidators(
      ValidatorClientConfiguration config,
      ValidatorApiChannel validatorApiChannel,
      AsyncRunner asyncRunner) {
    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();
    this.validatorIndexProvider =
        new ValidatorIndexProvider(validators, validatorApiChannel, asyncRunner);
    final BlockDutyFactory blockDutyFactory =
        new BlockDutyFactory(forkProvider, validatorApiChannel, spec);
    final AttestationDutyFactory attestationDutyFactory =
        new AttestationDutyFactory(forkProvider, validatorApiChannel);
    final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
        new BeaconCommitteeSubscriptions(validatorApiChannel);
    final DutyLoader<?> attestationDutyLoader =
        new RetryingDutyLoader<>(
            asyncRunner,
            new AttestationDutyLoader(
                validatorApiChannel,
                forkProvider,
                dependentRoot ->
                    new SlotBasedScheduledDuties<>(attestationDutyFactory, dependentRoot),
                validators,
                validatorIndexProvider,
                beaconCommitteeSubscriptions,
                spec));
    final DutyLoader<?> blockDutyLoader =
        new RetryingDutyLoader<>(
            asyncRunner,
            new BlockProductionDutyLoader(
                validatorApiChannel,
                dependentRoot -> new SlotBasedScheduledDuties<>(blockDutyFactory, dependentRoot),
                validators,
                validatorIndexProvider));
    final boolean useDependentRoots = config.getValidatorConfig().useDependentRoots();
    validatorTimingChannels.add(
        new BlockDutyScheduler(metricsSystem, blockDutyLoader, useDependentRoots, spec));
    validatorTimingChannels.add(
        new AttestationDutyScheduler(
            metricsSystem, attestationDutyLoader, useDependentRoots, spec));

    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      final ChainHeadTracker chainHeadTracker = new ChainHeadTracker();
      validatorTimingChannels.add(chainHeadTracker);
      final DutyLoader<SyncCommitteeScheduledDuties> syncCommitteeDutyLoader =
          new RetryingDutyLoader<>(
              asyncRunner,
              new SyncCommitteeDutyLoader(
                  validators,
                  validatorIndexProvider,
                  spec,
                  validatorApiChannel,
                  chainHeadTracker,
                  forkProvider));
      validatorTimingChannels.add(
          new SyncCommitteeScheduler(
              metricsSystem, spec, syncCommitteeDutyLoader, new Random()::nextInt));
    }

    if (spec.isMilestoneSupported(SpecMilestone.MERGE)) {
      validatorTimingChannels.add(
          new BeaconProposerPreparer(
              validatorApiChannel,
              validatorIndexProvider,
              config.getValidatorConfig().getSuggestedFeeRecipient(),
              validators,
              spec));
    }
    addValidatorCountMetric(metricsSystem, validators);
    this.validatorStatusLogger =
        new DefaultValidatorStatusLogger(
            metricsSystem, validators, validatorApiChannel, asyncRunner);
  }

  public static Path getSlashingProtectionPath(final DataDirLayout dataDirLayout) {
    return dataDirLayout.getValidatorDataDirectory().resolve("slashprotection");
  }

  public static Path getAlterableKeystorePath(final DataDirLayout dataDirLayout) {
    return dataDirLayout.getValidatorDataDirectory().resolve("alterable-keystores");
  }

  public static Path getAlterableKeystorePasswordPath(final DataDirLayout dataDirLayout) {
    return dataDirLayout.getValidatorDataDirectory().resolve("alterable-passwords");
  }

  private static void addValidatorCountMetric(
      final MetricsSystem metricsSystem, final OwnedValidators validators) {
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "local_validator_count",
        "Current number of validators running in this validator client",
        validators::getValidatorCount);
  }

  @Override
  protected SafeFuture<?> doStart() {
    return initializationComplete.thenCompose(
        (__) -> {
          SystemSignalListener.registerReloadConfigListener(validatorLoader::loadValidators);
          validatorIndexProvider.lookupValidators();
          eventChannels.subscribe(
              ValidatorTimingChannel.class,
              new ValidatorTimingActions(
                  validatorStatusLogger,
                  validatorIndexProvider,
                  validatorTimingChannels,
                  spec,
                  metricsSystem));
          validatorStatusLogger.printInitialValidatorStatuses().reportExceptions();
          validatorRestApi.ifPresent(restApi -> restApi.start().reportExceptions());
          return beaconNodeApi.subscribeToEvents();
        });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(
        SafeFuture.fromRunnable(
            () -> validatorRestApi.ifPresent(restApi -> restApi.stop().reportExceptions())),
        beaconNodeApi.unsubscribeFromEvents());
  }
}
