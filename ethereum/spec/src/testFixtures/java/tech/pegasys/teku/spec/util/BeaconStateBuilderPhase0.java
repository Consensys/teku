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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.ssz.SszList;

public class BeaconStateBuilderPhase0
    extends AbstractBeaconStateBuilder<
        BeaconStatePhase0, MutableBeaconStatePhase0, BeaconStateBuilderPhase0> {

  private SszList<PendingAttestation> previousEpochAttestations;
  private SszList<PendingAttestation> currentEpochAttestations;

  private BeaconStateBuilderPhase0(
      final Spec spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    super(spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected BeaconStatePhase0 getEmptyState() {
    return BeaconStateSchemaPhase0.create(spec.getGenesisSpecConfig()).createEmpty();
  }

  @Override
  protected void setUniqueFields(final MutableBeaconStatePhase0 state) {
    state.getPrevious_epoch_attestations().setAll(previousEpochAttestations);
    state.getCurrent_epoch_attestations().setAll(currentEpochAttestations);
  }

  public static BeaconStateBuilderPhase0 create(
      final DataStructureUtil dataStructureUtil,
      final Spec spec,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilderPhase0(
        spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected void initDefaults() {
    super.initDefaults();

    previousEpochAttestations =
        dataStructureUtil.randomSszList(
            dataStructureUtil.getBeaconStateSchema().getPreviousEpochAttestationsSchema(),
            defaultItemsInSSZLists,
            dataStructureUtil::randomPendingAttestation);
    currentEpochAttestations =
        dataStructureUtil.randomSszList(
            dataStructureUtil.getBeaconStateSchema().getCurrentEpochAttestationsSchema(),
            defaultItemsInSSZLists,
            dataStructureUtil::randomPendingAttestation);
  }

  public BeaconStateBuilderPhase0 previousEpochAttestations(
      final SszList<PendingAttestation> previousEpochAttestations) {
    checkNotNull(previousEpochAttestations);
    this.previousEpochAttestations = previousEpochAttestations;
    return this;
  }

  public BeaconStateBuilderPhase0 currentEpochAttestations(
      final SszList<PendingAttestation> currentEpochAttestations) {
    checkNotNull(currentEpochAttestations);
    this.currentEpochAttestations = currentEpochAttestations;
    return this;
  }
}
