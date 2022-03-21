/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.fuzz.input;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class SyncAggregateFuzzInput
    extends Container2<SyncAggregateFuzzInput, BeaconState, SyncAggregate> {

  public static ContainerSchema2<SyncAggregateFuzzInput, BeaconState, SyncAggregate> createSchema(
      final SpecVersion spec, final SpecConfigAltair config) {
    return ContainerSchema2.create(
        SszSchema.as(BeaconState.class, spec.getSchemaDefinitions().getBeaconStateSchema()),
        SyncAggregateSchema.create(config.getSyncCommitteeSize()),
        SyncAggregateFuzzInput::new);
  }

  public SyncAggregateFuzzInput(
      ContainerSchema2<SyncAggregateFuzzInput, BeaconState, SyncAggregate> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public SyncAggregateFuzzInput(
      final Spec spec,
      final SpecConfigAltair config,
      final BeaconState state,
      final SyncAggregate sync_aggregate) {
    super(createSchema(spec.atSlot(state.getSlot()), config), state, sync_aggregate);
  }

  public SyncAggregate getSyncAggregate() {
    return getField1();
  }

  public BeaconState getState() {
    return getField0();
  }
}
