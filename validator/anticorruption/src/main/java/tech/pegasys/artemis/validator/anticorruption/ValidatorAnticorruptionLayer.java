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

package tech.pegasys.artemis.validator.anticorruption;

import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.storage.api.ReorgEventChannel;
import tech.pegasys.artemis.util.time.channels.SlotEventsChannel;
import tech.pegasys.artemis.validator.api.ValidatorTimingChannel;

public class ValidatorAnticorruptionLayer {

  public static void initAnticorruptionLayer(final ServiceConfig config) {
    final ValidatorTimingChannel validatorTimingChannel =
        config.getEventChannels().getPublisher(ValidatorTimingChannel.class);
    final BeaconChainEventAdapter beaconChainEventAdapter =
        new BeaconChainEventAdapter(validatorTimingChannel);
    config.getEventBus().register(beaconChainEventAdapter);
    config
        .getEventChannels()
        .subscribe(SlotEventsChannel.class, beaconChainEventAdapter)
        .subscribe(ReorgEventChannel.class, beaconChainEventAdapter);
  }
}
