/*
 * Copyright Consensys Software Inc., 2025
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

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Optional;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class AttestationDutyLoader
    extends AbstractDutyLoader<AttesterDuties, SlotBasedScheduledDuties<?, ?>> {

  private final ValidatorApiChannel validatorApiChannel;
  private final AttestationDutySchedulingStrategySelector attestationDutySchedulingStrategySelector;

  public AttestationDutyLoader(
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final ValidatorApiChannel validatorApiChannel,
      final AttestationDutySchedulingStrategySelector attestationDutySchedulingStrategySelector) {
    super(validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.attestationDutySchedulingStrategySelector = attestationDutySchedulingStrategySelector;
  }

  @Override
  protected SafeFuture<Optional<AttesterDuties>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return validatorApiChannel.getAttestationDuties(epoch, validatorIndices);
  }

  @Override
  protected SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final AttesterDuties duties) {
    return attestationDutySchedulingStrategySelector
        .selectStrategy(duties.getDuties().size())
        .scheduleAllDuties(epoch, duties);
  }
}
