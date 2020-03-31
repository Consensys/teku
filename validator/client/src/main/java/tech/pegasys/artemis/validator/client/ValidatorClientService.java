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

package tech.pegasys.artemis.validator.client;

import java.util.Map;
import tech.pegasys.artemis.events.EventChannels;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.DelayedExecutorAsyncRunner;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.validator.anticorruption.ValidatorAnticorruptionLayer;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorTimingChannel;
import tech.pegasys.artemis.validator.client.duties.ValidatorDutyFactory;
import tech.pegasys.artemis.validator.client.loader.ValidatorLoader;

public class ValidatorClientService extends Service {
  private final EventChannels eventChannels;
  private final DutyScheduler dutyScheduler;

  private ValidatorClientService(
      final EventChannels eventChannels, final DutyScheduler dutyScheduler) {
    this.eventChannels = eventChannels;
    this.dutyScheduler = dutyScheduler;
  }

  public static ValidatorClientService create(final ServiceConfig config) {
    final EventChannels eventChannels = config.getEventChannels();
    final ValidatorApiChannel validatorApiChannel =
        eventChannels.getPublisher(ValidatorApiChannel.class);
    final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
    final ForkProvider forkProvider = new ForkProvider(asyncRunner, validatorApiChannel);
    final Map<BLSPublicKey, Validator> validators =
        ValidatorLoader.initializeValidators(config.getConfig());
    final ValidatorDutyFactory validatorDutyFactory =
        new ValidatorDutyFactory(forkProvider, validatorApiChannel);
    final DutyScheduler validatorClient =
        new DutyScheduler(asyncRunner, validatorApiChannel, validatorDutyFactory, validators);

    ValidatorAnticorruptionLayer.initAnticorruptionLayer(config);

    return new ValidatorClientService(eventChannels, validatorClient);
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventChannels.subscribe(ValidatorTimingChannel.class, dutyScheduler);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }
}
