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
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;

public class BeaconStateSchemaPhase0
    extends AbstractBeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> {

  @VisibleForTesting
  BeaconStateSchemaPhase0(final SpecConfig specConfig) {
    super("BeaconStatePhase0", getUniqueFields(specConfig), specConfig);
  }

  public static BeaconStateSchemaPhase0 create(final SpecConfig specConfig) {
    return new BeaconStateSchemaPhase0(specConfig);
  }

  private static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final SszField previousEpochAttestationsField =
        new SszField(
            15,
            BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name(),
            () ->
                SszListSchema.create(
                    PendingAttestation.SSZ_SCHEMA,
                    (long) specConfig.getMaxAttestations() * specConfig.getSlotsPerEpoch()));
    final SszField currentEpochAttestationsField =
        new SszField(
            16,
            BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name(),
            () ->
                SszListSchema.create(
                    PendingAttestation.SSZ_SCHEMA,
                    (long) specConfig.getMaxAttestations() * specConfig.getSlotsPerEpoch()));

    return List.of(previousEpochAttestationsField, currentEpochAttestationsField);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingAttestation, ?> getPreviousEpochAttestationsSchema() {
    return (SszListSchema<PendingAttestation, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name()));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingAttestation, ?> getCurrentEpochAttestationsSchema() {
    return (SszListSchema<PendingAttestation, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name()));
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
