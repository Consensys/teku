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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszField;

public class BeaconStateSchemaPhase0
    extends AbstractBeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> {

  @VisibleForTesting
  BeaconStateSchemaPhase0(final SpecConstants specConstants) {
    super("BeaconStatePhase0", getUniqueFields(specConstants), specConstants);
  }

  public static BeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> create(
      final SpecConstants specConstants) {
    return new BeaconStateSchemaPhase0(specConstants);
  }

  private static List<SszField> getUniqueFields(final SpecConstants specConstants) {
    final SszField previousEpochAttestationsField =
        new SszField(
            15,
            BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name(),
            () ->
                SszListSchema.create(
                    PendingAttestation.SSZ_SCHEMA,
                    (long) specConstants.getMaxAttestations() * specConstants.getSlotsPerEpoch()));
    final SszField currentEpochAttestationsField =
        new SszField(
            16,
            BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name(),
            () ->
                SszListSchema.create(
                    PendingAttestation.SSZ_SCHEMA,
                    (long) specConstants.getMaxAttestations() * specConstants.getSlotsPerEpoch()));

    return List.of(previousEpochAttestationsField, currentEpochAttestationsField);
  }

  @Override
  public BeaconStatePhase0 createFromBackingNode(TreeNode node) {
    return new BeaconStatePhase0Impl(this, node);
  }

  @Override
  public MutableBeaconStatePhase0 createBuilder() {
    return new MutableBeaconStatePhase0Impl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStatePhase0 createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStatePhase0Impl createEmptyBeaconStateImpl() {
    return new BeaconStatePhase0Impl(this);
  }
}
