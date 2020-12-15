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
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
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
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyFactory;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.eventadapter.InProcessBeaconNodeApi;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi;

public class ValidatorClientService extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final BeaconNodeApi beaconNodeApi;
  private final ForkProvider forkProvider;

  private ValidatorTimingChannel attestationTimingChannel;
  private ValidatorTimingChannel blockProductionTimingChannel;
  private ValidatorStatusLogger validatorStatusLogger;
  private ValidatorIndexProvider validatorIndexProvider;

  private SafeFuture<Void> initializationComplete = new SafeFuture<>();

  private ValidatorClientService(
      final EventChannels eventChannels,
      final BeaconNodeApi beaconNodeApi,
      final ForkProvider forkProvider) {
    this.eventChannels = eventChannels;
    this.beaconNodeApi = beaconNodeApi;
    this.forkProvider = forkProvider;
  }

  public static ValidatorClientService create(
      final ServiceConfig services, final ValidatorClientConfiguration config) {
    final EventChannels eventChannels = services.getEventChannels();
    final AsyncRunner asyncRunner = services.createAsyncRunner("validator");
    final BeaconNodeApi beaconNodeApi =
        config
            .getValidatorConfig()
            .getBeaconNodeApiEndpoint()
            .map(endpoint -> RemoteBeaconNodeApi.create(services, asyncRunner, endpoint))
            .orElseGet(() -> InProcessBeaconNodeApi.create(services, asyncRunner));
    final ValidatorApiChannel validatorApiChannel = beaconNodeApi.getValidatorApi();
    final GenesisDataProvider genesisDataProvider =
        new GenesisDataProvider(asyncRunner, validatorApiChannel);
    final ForkProvider forkProvider =
        new ForkProvider(asyncRunner, validatorApiChannel, genesisDataProvider);

    ValidatorClientService validatorClientService =
        new ValidatorClientService(eventChannels, beaconNodeApi, forkProvider);

    asyncRunner
        .runAsync(
            () ->
                validatorClientService.initializeValidators(
                    config, validatorApiChannel, asyncRunner, services))
        .finish(err -> LOG.error("Unable to initialize validators", err));

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
        ValidatorLoader.create(slashingProtector, asyncRunner, metricsSystem);
    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(
            config.getValidatorConfig(), config.getGlobalConfiguration());
    this.validatorIndexProvider =
        new ValidatorIndexProvider(validators.keySet(), validatorApiChannel);
    final ValidatorDutyFactory validatorDutyFactory =
        new ValidatorDutyFactory(forkProvider, validatorApiChannel);
    final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
        new BeaconCommitteeSubscriptions(validatorApiChannel);
    final DutyLoader attestationDutyLoader =
        new RetryingDutyLoader(
            asyncRunner,
            new AttestationDutyLoader(
                validatorApiChannel,
                forkProvider,
                () -> new ScheduledDuties(validatorDutyFactory),
                validators,
                validatorIndexProvider,
                beaconCommitteeSubscriptions));
    final DutyLoader blockDutyLoader =
        new RetryingDutyLoader(
            asyncRunner,
            new BlockProductionDutyLoader(
                validatorApiChannel,
                () -> new ScheduledDuties(validatorDutyFactory),
                validators,
                validatorIndexProvider));
    this.attestationTimingChannel =
        new AttestationDutyScheduler(metricsSystem, attestationDutyLoader);
    this.blockProductionTimingChannel = new BlockDutyScheduler(metricsSystem, blockDutyLoader);
    addValidatorCountMetric(metricsSystem, validators.size());
    if (validators.keySet().size() > 0) {
      this.validatorStatusLogger =
          new DefaultValidatorStatusLogger(
              new ArrayList<>(validators.keySet()), validatorApiChannel, asyncRunner);
    } else {
      this.validatorStatusLogger = ValidatorStatusLogger.NOOP;
    }
    initializationComplete.complete(null);
  }

  public static Path getSlashingProtectionPath(final DataDirLayout dataDirLayout) {
    return dataDirLayout.getValidatorDataDirectory().resolve("slashprotection");
  }

  private static void addValidatorCountMetric(
      final MetricsSystem metricsSystem, final int validatorCount) {
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "local_validator_count",
        "Current number of validators running in this validator client",
        () -> validatorCount);
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
                  attestationTimingChannel));
          validatorStatusLogger.printInitialValidatorStatuses().reportExceptions();
          return beaconNodeApi.subscribeToEvents();
        });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return beaconNodeApi.unsubscribeFromEvents();
  }
}
