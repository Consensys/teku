/*
 * Copyright ConsenSys Software Inc., 2023
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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;

public class VoluntaryExitDataProvider {
  private final Spec spec;
  private final TimeProvider timeProvider;

  VoluntaryExitDataProvider(final Spec spec, final TimeProvider timeProvider) {
    this.spec = spec;
    this.timeProvider = timeProvider;
  }

  protected UInt64 getOrCalculateCurrentEpoch(
      final Optional<UInt64> maybeEpoch, final UInt64 genesisTime) {
    return maybeEpoch.orElseGet(
        () -> {
          final SpecVersion genesisSpec = spec.getGenesisSpec();
          final UInt64 currentTime = timeProvider.getTimeInSeconds();
          final UInt64 slot = genesisSpec.miscHelpers().computeSlotAtTime(genesisTime, currentTime);
          return spec.computeEpochAtSlot(slot);
        });
  }
}
