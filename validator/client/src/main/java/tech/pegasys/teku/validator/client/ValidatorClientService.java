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
import java.util.Map;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
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
  private final EventChannels eventChannels;
  private final ValidatorTimingChannel attestationTimingChannel;
  private final ValidatorTimingChannel blockProductionTimingChannel;
  private final ValidatorIndexProvider validatorIndexProvider;
  private final BeaconNodeApi beaconNodeApi;

  private ValidatorClientService(
      final EventChannels eventChannels,
      final ValidatorTimingChannel attestationTimingChannel,
      final ValidatorTimingChannel blockProductionTimingChannel,
      final ValidatorIndexProvider validatorIndexProvider,
      final BeaconNodeApi beaconNodeApi) {
    this.eventChannels = eventChannels;
    this.attestationTimingChannel = attestationTimingChannel;
    this.blockProductionTimingChannel = blockProductionTimingChannel;
    this.validatorIndexProvider = validatorIndexProvider;
    this.beaconNodeApi = beaconNodeApi;
  }

  public static ValidatorClientService create(
      final ServiceConfig services, final ValidatorClientConfiguration config) {
    final EventChannels eventChannels = services.getEventChannels();
    final MetricsSystem metricsSystem = services.getMetricsSystem();
    final AsyncRunner asyncRunner = services.createAsyncRunner("validator");
    final Path slashingProtectionPath = getSlashingProtectionPath(services.getDataDirLayout());
    final SlashingProtector slashingProtector =
        new SlashingProtector(new SyncDataAccessor(), slashingProtectionPath);
    final ValidatorLoader validatorLoader = new ValidatorLoader(slashingProtector, asyncRunner);
    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(
            config.getValidatorConfig(), config.getGlobalConfiguration());

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
    final ValidatorIndexProvider validatorIndexProvider =
        new ValidatorIndexProvider(validators.keySet(), validatorApiChannel);
    final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
        new BeaconCommitteeSubscriptions(validatorApiChannel);
    final ValidatorDutyFactory validatorDutyFactory =
        new ValidatorDutyFactory(forkProvider, validatorApiChannel);
    final DutyLoader attestationDutyLoader =
        new RetryingDutyLoader(
            asyncRunner,
            new AttestationDutyLoader(
                validatorApiChannel,
                forkProvider,
                targetRoot -> new ScheduledDuties(validatorDutyFactory, targetRoot),
                validators,
                validatorIndexProvider,
                beaconCommitteeSubscriptions));
    final DutyLoader blockDutyLoader =
        new RetryingDutyLoader(
            asyncRunner,
            new BlockProductionDutyLoader(
                validatorApiChannel,
                targetRoot -> new ScheduledDuties(validatorDutyFactory, targetRoot),
                validators,
                validatorIndexProvider));
    final AttestationDutyScheduler attestationDutyScheduler =
        new AttestationDutyScheduler(metricsSystem, attestationDutyLoader);
    final BlockDutyScheduler blockDutyScheduler =
        new BlockDutyScheduler(metricsSystem, blockDutyLoader);

    addValidatorCountMetric(metricsSystem, validators.size());

    return new ValidatorClientService(
        eventChannels,
        attestationDutyScheduler,
        blockDutyScheduler,
        validatorIndexProvider,
        beaconNodeApi);
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
    eventChannels.subscribe(
        ValidatorTimingChannel.class,
        new ValidatorTimingActions(
            validatorIndexProvider, blockProductionTimingChannel, attestationTimingChannel));
    validatorIndexProvider.lookupValidators();
    return beaconNodeApi.subscribeToEvents();
  }

  @Override
  protected SafeFuture<?> doStop() {
    return beaconNodeApi.unsubscribeFromEvents();
  }
}
