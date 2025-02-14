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
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.InclusionListCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.InclusionListProductionDuty;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class InclusionListDutyLoader
    extends AbstractDutyLoader<InclusionListDuties, SlotBasedScheduledDuties<?, ?>> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private final Function<Bytes32, SlotBasedScheduledDuties<InclusionListProductionDuty, Duty>>
      scheduledDutiesFactory;
  private final InclusionListCommitteeSubscriptions inclusionListCommitteeSubscriptions;

  protected InclusionListDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final Function<Bytes32, SlotBasedScheduledDuties<InclusionListProductionDuty, Duty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final InclusionListCommitteeSubscriptions inclusionListCommitteeSubscriptions) {
    super(validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
    this.inclusionListCommitteeSubscriptions = inclusionListCommitteeSubscriptions;
  }

  @Override
  protected SafeFuture<Optional<InclusionListDuties>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return validatorApiChannel.getInclusionListDuties(epoch, validatorIndices);
  }

  // TODO EIP7805 implement duties scheduling
  @Override
  protected SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final InclusionListDuties duties) {
    final SlotBasedScheduledDuties<InclusionListProductionDuty, Duty> scheduledDuties =
        scheduledDutiesFactory.apply(duties.dependentRoot());
    return SafeFuture.allOf(
            duties.duties().stream()
                .map(duty -> scheduleDuties(scheduledDuties, duty))
                .toArray(SafeFuture[]::new))
        .<SlotBasedScheduledDuties<?, ?>>thenApply(__ -> scheduledDuties)
        .alwaysRun(inclusionListCommitteeSubscriptions::sendRequests);
  }

  @SuppressWarnings("unused")
  private SafeFuture<Void> scheduleDuties(
      final SlotBasedScheduledDuties<InclusionListProductionDuty, Duty> scheduledDuties,
      final InclusionListDuty duty) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
