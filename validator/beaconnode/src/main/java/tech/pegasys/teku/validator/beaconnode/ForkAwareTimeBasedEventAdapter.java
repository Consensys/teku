/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.beaconnode;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class ForkAwareTimeBasedEventAdapter implements BeaconChainEventAdapter {

  final TimeBasedEventAdapter delegate;

  public ForkAwareTimeBasedEventAdapter(
      final GenesisDataProvider genesisDataProvider,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final Spec spec) {
    if (spec.isMilestoneSupported(SpecMilestone.EIP7732)) {
      delegate =
          new GloasTimeBasedEventAdapter(
              genesisDataProvider, taskScheduler, timeProvider, validatorTimingChannel, spec);
    } else {
      delegate =
          new Phase0TimeBasedEventAdapter(
              genesisDataProvider, taskScheduler, timeProvider, validatorTimingChannel, spec);
    }
  }

  @Override
  public SafeFuture<Void> start() {
    return delegate.start();
  }

  @Override
  public SafeFuture<Void> stop() {
    return delegate.stop();
  }
}
