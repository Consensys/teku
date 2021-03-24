/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch;

import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.AbstractEpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;

public class EpochProcessorAltair extends AbstractEpochProcessor {

  private final SpecConfigAltair altairSpecConfig;
  private final BeaconStateAccessorsAltair altairBeaconStateAccessors;

  public EpochProcessorAltair(
      final SpecConfigAltair altairSpecConfig,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final BeaconStateAccessorsAltair altairBeaconStateAccessors) {
    super(
        altairSpecConfig,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        altairBeaconStateAccessors);
    this.altairSpecConfig = altairSpecConfig;
    this.altairBeaconStateAccessors = altairBeaconStateAccessors;
  }

  @Override
  protected void processEpoch(
      final MutableBeaconState state, final ValidatorStatuses validatorStatuses)
      throws EpochProcessingException {
    super.processEpoch(state, validatorStatuses);
    processSyncCommitteeUpdates(state.toMutableVersionAltair().orElseThrow());
  }

  @Override
  public void processParticipationUpdates(final MutableBeaconState genericState) {
    throw new NotImplementedException("TODO");
  }

  protected void processSyncCommitteeUpdates(final MutableBeaconStateAltair state) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).increment();
    if (nextEpoch.mod(altairSpecConfig.getEpochsPerSyncCommitteePeriod()).isZero()) {
      state.setCurrentSyncCommittee(state.getNextSyncCommittee());
      state.setNextSyncCommittee(
          altairBeaconStateAccessors.getSyncCommittee(
              state, nextEpoch.plus(altairSpecConfig.getEpochsPerSyncCommitteePeriod())));
    }
  }
}
