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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis.BeaconStateGenesis;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis.BeaconStateSchemaGenesis;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis.MutableBeaconStateGenesis;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class BeaconStateBuilderGenesis
    extends AbstractBeaconStateBuilder<
        BeaconStateGenesis, MutableBeaconStateGenesis, BeaconStateBuilderGenesis> {

  private SSZList<PendingAttestation> previousEpochAttestations;
  private SSZList<PendingAttestation> currentEpochAttestations;

  private BeaconStateBuilderGenesis(
      final Spec spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    super(spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected BeaconStateGenesis getEmptyState() {
    return BeaconStateSchemaGenesis.create(spec.getGenesisSpecConstants()).createEmpty();
  }

  @Override
  protected void setUniqueFields(final MutableBeaconStateGenesis state) {
    state.getPrevious_epoch_attestations().setAll(previousEpochAttestations);
    state.getCurrent_epoch_attestations().setAll(currentEpochAttestations);
  }

  public static BeaconStateBuilderGenesis create(
      final DataStructureUtil dataStructureUtil,
      final Spec spec,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilderGenesis(
        spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected void initDefaults() {
    super.initDefaults();

    previousEpochAttestations =
        dataStructureUtil.randomSSZList(
            PendingAttestation.class,
            defaultItemsInSSZLists,
            dataStructureUtil.getMaxAttestations() * dataStructureUtil.getSlotsPerEpoch(),
            dataStructureUtil::randomPendingAttestation);
    currentEpochAttestations =
        dataStructureUtil.randomSSZList(
            PendingAttestation.class,
            defaultItemsInSSZLists,
            dataStructureUtil.getMaxAttestations() * dataStructureUtil.getSlotsPerEpoch(),
            dataStructureUtil::randomPendingAttestation);
  }

  public BeaconStateBuilderGenesis previousEpochAttestations(
      final SSZList<PendingAttestation> previousEpochAttestations) {
    checkNotNull(previousEpochAttestations);
    this.previousEpochAttestations = previousEpochAttestations;
    return this;
  }

  public BeaconStateBuilderGenesis currentEpochAttestations(
      final SSZList<PendingAttestation> currentEpochAttestations) {
    checkNotNull(currentEpochAttestations);
    this.currentEpochAttestations = currentEpochAttestations;
    return this;
  }
}
