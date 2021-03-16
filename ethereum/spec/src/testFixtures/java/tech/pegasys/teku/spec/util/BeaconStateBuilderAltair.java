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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.primitive.SszByte;

public class BeaconStateBuilderAltair
    extends AbstractBeaconStateBuilder<
        BeaconStateAltair, MutableBeaconStateAltair, BeaconStateBuilderAltair> {

  private SszList<SszByte> previousEpochParticipation;
  private SszList<SszByte> currentEpochParticipation;

  private BeaconStateBuilderAltair(
      final Spec spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    super(spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected BeaconStateAltair getEmptyState() {
    return BeaconStateSchemaAltair.create(spec.getGenesisSpecConstants()).createEmpty();
  }

  @Override
  protected void setUniqueFields(final MutableBeaconStateAltair state) {
    state.getPreviousEpochParticipation().setAll(previousEpochParticipation);
    state.getCurrentEpochParticipation().setAll(currentEpochParticipation);
  }

  public static BeaconStateBuilderAltair create(
      final DataStructureUtil dataStructureUtil,
      final Spec spec,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilderAltair(
        spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  private BeaconStateSchemaAltair getBeaconStateSchemaAltair() {
    return (BeaconStateSchemaAltair) getEmptyState().getSchema();
  }

  @Override
  protected void initDefaults() {
    super.initDefaults();

    previousEpochParticipation =
        dataStructureUtil.randomSszList(
            getBeaconStateSchemaAltair().getPreviousEpochParticipationSchema(),
            defaultItemsInSSZLists,
            dataStructureUtil::randomSszByte);
    currentEpochParticipation =
        dataStructureUtil.randomSszList(
            getBeaconStateSchemaAltair().getCurrentEpochParticipationSchema(),
            defaultItemsInSSZLists,
            dataStructureUtil::randomSszByte);
  }

  public BeaconStateBuilderAltair previousEpochAttestations(final SszList<SszByte> value) {
    checkNotNull(value);
    this.previousEpochParticipation = value;
    return this;
  }

  public BeaconStateBuilderAltair currentEpochAttestations(final SszList<SszByte> value) {
    checkNotNull(value);
    this.currentEpochParticipation = value;
    return this;
  }
}
