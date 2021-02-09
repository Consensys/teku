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

package tech.pegasys.teku.spec.datastructures.state.genesis;

import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateSchema;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SszField;

public class BeaconStateSchemaGenesis extends SszContainerSchema<BeaconStateGenesis>
    implements BeaconStateSchema<BeaconStateGenesis> {

  public static List<SszField> getGenesisFields(final SpecConstants specConstants) {
    SszField previous_epoch_attestations_field =
        new SszField(
            15,
            BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name(),
            () ->
                new SszListSchema<>(
                    PendingAttestation.SSZ_SCHEMA,
                    specConstants.getMaxAttestations() * specConstants.getSlotsPerEpoch()));
    SszField current_epoch_attestations_field =
        new SszField(
            16,
            BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name(),
            () ->
                new SszListSchema<>(
                    PendingAttestation.SSZ_SCHEMA,
                    specConstants.getMaxAttestations() * specConstants.getSlotsPerEpoch()));

    return List.of(previous_epoch_attestations_field, current_epoch_attestations_field);
  }

  BeaconStateSchemaGenesis(final List<NamedSchema<?>> fieldSchemas) {
    super("BeaconState", fieldSchemas);
  }

  public static BeaconStateSchemaGenesis create(List<SszField> fields) {
    final List<NamedSchema<?>> namedFields =
        fields.stream()
            .map(f -> namedSchema(f.getName(), f.getSchema().get()))
            .collect(Collectors.toList());
    return new BeaconStateSchemaGenesis(namedFields);
  }

  @Override
  public BeaconStateGenesis createFromBackingNode(final TreeNode node) {
    return new BeaconStateGenesisImpl(this, node);
  }
}
