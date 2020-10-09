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
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyFactory;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.eventadapter.InProcessBeaconNodeApi;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi;

public class ValidatorClientService extends Service {
  private final EventChannels eventChannels;
  private final ValidatorTimingChannel attestationTimingChannel;
  private final ValidatorTimingChannel blockProductionTimingChannel;
  private final BeaconNodeApi beaconNodeApi;

  private ValidatorClientService(
      final EventChannels eventChannels,
      final ValidatorTimingChannel attestationTimingChannel,
      final ValidatorTimingChannel blockProductionTimingChannel,
      final BeaconNodeApi beaconNodeApi) {
    this.eventChannels = eventChannels;
    this.attestationTimingChannel = attestationTimingChannel;
    this.blockProductionTimingChannel = blockProductionTimingChannel;
    this.beaconNodeApi = beaconNodeApi;
  }

  public static ValidatorClientService create(
      final ServiceConfig services, final ValidatorClientConfiguration config) {
    final EventChannels eventChannels = services.getEventChannels();
    final MetricsSystem metricsSystem = services.getMetricsSystem();
    final AsyncRunner asyncRunner = services.createAsyncRunner("validator");
    final Path slashingProtectionPath = services.getConfig().getValidatorsSlashingProtectionPath();
    final SlashingProtector slashingProtector =
        new SlashingProtector(new SyncDataAccessor(), slashingProtectionPath);
    final ValidatorLoader validatorLoader = new ValidatorLoader(slashingProtector, asyncRunner);
    final Map<BLSPublicKey, Validator> validators =
        validatorLoader.initializeValidators(
            config.getValidatorConfig(), config.getGlobalConfiguration());

    final BeaconNodeApi beaconNodeApi;
    if (services.getConfig().isValidatorClient()) {
      beaconNodeApi = RemoteBeaconNodeApi.create(services, asyncRunner);
    } else {
      beaconNodeApi = InProcessBeaconNodeApi.create(services, asyncRunner);
    }

    final ValidatorApiChannel validatorApiChannel = beaconNodeApi.getValidatorApi();
    final RetryingDutyLoader dutyLoader =
        createDutyLoader(metricsSystem, validatorApiChannel, asyncRunner, validators);
    final StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(validatorApiChannel, new Random(), validators.size());
    final AttestationDutyScheduler attestationDutyScheduler =
        new AttestationDutyScheduler(metricsSystem, dutyLoader, stableSubnetSubscriber);
    final BlockDutyScheduler blockDutyScheduler = new BlockDutyScheduler(metricsSystem, dutyLoader);
    return new ValidatorClientService(
        eventChannels, attestationDutyScheduler, blockDutyScheduler, beaconNodeApi);
  }

  private static RetryingDutyLoader createDutyLoader(
      final MetricsSystem metricsSystem,
      final ValidatorApiChannel validatorApiChannel,
      final AsyncRunner asyncRunner,
      final Map<BLSPublicKey, Validator> validators) {
    final ForkProvider forkProvider = new ForkProvider(asyncRunner, validatorApiChannel);
    final ValidatorDutyFactory validatorDutyFactory =
        new ValidatorDutyFactory(forkProvider, validatorApiChannel);
    return new RetryingDutyLoader(
        asyncRunner,
        new ValidatorApiDutyLoader(
            metricsSystem,
            validatorApiChannel,
            forkProvider,
            () -> new ScheduledDuties(validatorDutyFactory),
            validators));
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventChannels.subscribe(ValidatorTimingChannel.class, blockProductionTimingChannel);
    eventChannels.subscribe(ValidatorTimingChannel.class, attestationTimingChannel);
    return beaconNodeApi.subscribeToEvents();
  }

  @Override
  protected SafeFuture<?> doStop() {
    return beaconNodeApi.unsubscribeFromEvents();
  }
}
