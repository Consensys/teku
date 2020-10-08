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
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyFactory;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.metrics.MetricRecordingValidatorApiChannel;
import tech.pegasys.teku.validator.client.time.GenesisTimeProvider;
import tech.pegasys.teku.validator.client.time.TimeBasedEventAdapter;
import tech.pegasys.teku.validator.eventadapter.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.eventadapter.EventChannelBeaconChainEventAdapter;
import tech.pegasys.teku.validator.eventadapter.IndependentTimerEventChannelEventAdapter;
import tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler;
import tech.pegasys.teku.validator.remote.WebSocketBeaconChainEventAdapter;

public class ValidatorClientService extends Service {

  private static final boolean USE_INDEPENDENT_TIMER = false;

  private final EventChannels eventChannels;
  private final ValidatorTimingChannel attestationTimingChannel;
  private final ValidatorTimingChannel blockProductionTimingChannel;
  private final BeaconChainEventAdapter beaconChainEventAdapter;

  private ValidatorClientService(
      final EventChannels eventChannels,
      final ValidatorTimingChannel attestationTimingChannel,
      final ValidatorTimingChannel blockProductionTimingChannel,
      final BeaconChainEventAdapter beaconChainEventAdapter) {
    this.eventChannels = eventChannels;
    this.attestationTimingChannel = attestationTimingChannel;
    this.blockProductionTimingChannel = blockProductionTimingChannel;
    this.beaconChainEventAdapter = beaconChainEventAdapter;
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

    final ValidatorApiChannel validatorApiChannel;
    if (services.getConfig().isValidatorClient()) {
      validatorApiChannel =
          new MetricRecordingValidatorApiChannel(
              metricsSystem, new RemoteValidatorApiHandler(services, asyncRunner));
    } else {
      validatorApiChannel =
          new MetricRecordingValidatorApiChannel(
              metricsSystem,
              services.getEventChannels().getPublisher(ValidatorApiChannel.class, asyncRunner));
    }
    final RetryingDutyLoader dutyLoader =
        createDutyLoader(metricsSystem, validatorApiChannel, asyncRunner, validators);
    final StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(validatorApiChannel, new Random(), validators.size());
    final AttestationDutyScheduler attestationDutyScheduler =
        new AttestationDutyScheduler(metricsSystem, dutyLoader, stableSubnetSubscriber);
    final BlockDutyScheduler blockDutyScheduler = new BlockDutyScheduler(metricsSystem, dutyLoader);

    final BeaconChainEventAdapter beaconChainEventAdapter;
    if (services.getConfig().isValidatorClient()) {
      beaconChainEventAdapter = new WebSocketBeaconChainEventAdapter(services);
    } else {
      if (USE_INDEPENDENT_TIMER) {
        final ValidatorTimingChannel validatorTimingChannel =
            eventChannels.getPublisher(ValidatorTimingChannel.class);
        final TimeBasedEventAdapter timeBasedEventAdapter =
            new TimeBasedEventAdapter(
                new GenesisTimeProvider(asyncRunner, validatorApiChannel),
                new RepeatingTaskScheduler(asyncRunner, services.getTimeProvider()),
                services.getTimeProvider(),
                validatorTimingChannel);
        beaconChainEventAdapter =
            new IndependentTimerEventChannelEventAdapter(
                eventChannels, timeBasedEventAdapter, validatorTimingChannel);
      } else {
        beaconChainEventAdapter = new EventChannelBeaconChainEventAdapter(services);
      }
    }

    return new ValidatorClientService(
        eventChannels, attestationDutyScheduler, blockDutyScheduler, beaconChainEventAdapter);
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
    return SafeFuture.of(beaconChainEventAdapter.start());
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.of(beaconChainEventAdapter.stop());
  }
}
