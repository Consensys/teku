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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.signatures.LocalSlashingProtector;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyFactory;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.eventadapter.InProcessBeaconNodeApi;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi;

public class ValidatorClientService extends Service {
  private final EventChannels eventChannels;
  private final BeaconNodeApi beaconNodeApi;
  private final ForkProvider forkProvider;
  private final SpecProvider specProvider;

  private ValidatorTimingChannel attestationTimingChannel;
  private ValidatorTimingChannel blockProductionTimingChannel;
  private ValidatorStatusLogger validatorStatusLogger;
  private ValidatorIndexProvider validatorIndexProvider;

  private final SafeFuture<Void> initializationComplete = new SafeFuture<>();

  private ValidatorClientService(
      final EventChannels eventChannels,
      final BeaconNodeApi beaconNodeApi,
      final ForkProvider forkProvider,
      final SpecProvider specProvider) {
    this.eventChannels = eventChannels;
    this.beaconNodeApi = beaconNodeApi;
    this.forkProvider = forkProvider;
    this.specProvider = specProvider;
  }

  public static ValidatorClientService create(
      final ServiceConfig services, final ValidatorClientConfiguration config) {
    final EventChannels eventChannels = services.getEventChannels();
    final AsyncRunner asyncRunner = services.createAsyncRunner("validator");
    final boolean useDependentRoots = config.getValidatorConfig().useDependentRoots();
    final BeaconNodeApi beaconNodeApi =
        config
            .getValidatorConfig()
            .getBeaconNodeApiEndpoint()
            .map(
                endpoint ->
                    RemoteBeaconNodeApi.create(
                        services,
                        asyncRunner,
                        endpoint,
                        config.getSpecProvider(),
                        useDependentRoots))
            .orElseGet(
                () -> InProcessBeaconNodeApi.create(services, asyncRunner, useDependentRoots));
    final ValidatorApiChannel validatorApiChannel = beaconNodeApi.getValidatorApi();
    final GenesisDataProvider genesisDataProvider =
        new GenesisDataProvider(asyncRunner, validatorApiChannel);
    final ForkProvider forkProvider =
        new ForkProvider(asyncRunner, validatorApiChannel, genesisDataProvider);

    ValidatorClientService validatorClientService =
        new ValidatorClientService(
            eventChannels, beaconNodeApi, forkProvider, config.getSpecProvider());

    asyncRunner
        .runAsync(
            () ->
                validatorClientService.initializeValidators(
                    config, validatorApiChannel, asyncRunner, services))
        .propagateTo(validatorClientService.initializationComplete);
    return validatorClientService;
  }

  void initializeValidators(
      ValidatorClientConfiguration config,
      ValidatorApiChannel validatorApiChannel,
      AsyncRunner asyncRunner,
      ServiceConfig services) {
    final MetricsSystem metricsSystem = services.getMetricsSystem();
    final Path slashingProtectionPath = getSlashingProtectionPath(services.getDataDirLayout());
    final SlashingProtector slashingProtector =
        new LocalSlashingProtector(new SyncDataAccessor(), slashingProtectionPath);
    final ValidatorLoader validatorLoader =
        ValidatorLoader.create(
            config.getValidatorConfig(),
            config.getInteropConfig(),
            slashingProtector,
            new PublicKeyLoader(),
            asyncRunner,
            metricsSystem);
    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();
    this.validatorIndexProvider = new ValidatorIndexProvider(validators, validatorApiChannel);
    final ValidatorDutyFactory validatorDutyFactory =
        new ValidatorDutyFactory(forkProvider, validatorApiChannel, specProvider);
    final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
        new BeaconCommitteeSubscriptions(validatorApiChannel);
    final DutyLoader attestationDutyLoader =
        new RetryingDutyLoader(
            asyncRunner,
            new AttestationDutyLoader(
                validatorApiChannel,
                forkProvider,
                dependentRoot -> new ScheduledDuties(validatorDutyFactory, dependentRoot),
                validators,
                validatorIndexProvider,
                beaconCommitteeSubscriptions,
                specProvider));
    final DutyLoader blockDutyLoader =
        new RetryingDutyLoader(
            asyncRunner,
            new BlockProductionDutyLoader(
                validatorApiChannel,
                dependentRoot -> new ScheduledDuties(validatorDutyFactory, dependentRoot),
                validators,
                validatorIndexProvider));
    final boolean useDependentRoots = config.getValidatorConfig().useDependentRoots();
    this.attestationTimingChannel =
        new AttestationDutyScheduler(
            metricsSystem, attestationDutyLoader, useDependentRoots, specProvider);
    this.blockProductionTimingChannel =
        new BlockDutyScheduler(metricsSystem, blockDutyLoader, useDependentRoots, specProvider);
    addValidatorCountMetric(metricsSystem, validators);
    this.validatorStatusLogger =
        new DefaultValidatorStatusLogger(validators, validatorApiChannel, asyncRunner);
  }

  public static Path getSlashingProtectionPath(final DataDirLayout dataDirLayout) {
    return dataDirLayout.getValidatorDataDirectory().resolve("slashprotection");
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
          forkProvider.start().reportExceptions();
          validatorIndexProvider.lookupValidators();
          eventChannels.subscribe(
              ValidatorTimingChannel.class,
              new ValidatorTimingActions(
                  validatorStatusLogger,
                  validatorIndexProvider,
                  blockProductionTimingChannel,
                  attestationTimingChannel,
                  specProvider));
          validatorStatusLogger.printInitialValidatorStatuses().reportExceptions();
          return beaconNodeApi.subscribeToEvents();
        });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return beaconNodeApi.unsubscribeFromEvents();
  }
}
