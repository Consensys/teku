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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.sos.SszField;

public class BeaconStateSchemaAltair
    extends AbstractBeaconStateSchema<BeaconStateAltair, MutableBeaconStateAltair> {

  private static final int PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX = 15;
  private static final int CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX = 16;

  @VisibleForTesting
  BeaconStateSchemaAltair(final SpecConstants specConstants) {
    super("BeaconStateAltair", getUniqueFields(specConstants), specConstants);
  }

  public static BeaconStateSchema<BeaconStateAltair, MutableBeaconStateAltair> create(
      final SpecConstants specConstants) {
    return new BeaconStateSchemaAltair(specConstants);
  }

  private static List<SszField> getUniqueFields(final SpecConstants specConstants) {
    final SszField previousEpochAttestationsField =
        new SszField(
            PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX,
            BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION.name(),
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.BYTE_SCHEMA, specConstants.getValidatorRegistryLimit()));
    final SszField currentEpochAttestationsField =
        new SszField(
            CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX,
            BeaconStateFields.CURRENT_EPOCH_PARTICIPATION.name(),
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.BYTE_SCHEMA, specConstants.getValidatorRegistryLimit()));

    return List.of(previousEpochAttestationsField, currentEpochAttestationsField);
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<Byte, SszByte, ?> getPreviousEpochParticipationSchema() {
    return (SszPrimitiveListSchema<Byte, SszByte, ?>)
        getChildSchema(PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX);
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<Byte, SszByte, ?> getCurrentEpochParticipationSchema() {
    return (SszPrimitiveListSchema<Byte, SszByte, ?>)
        getChildSchema(CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX);
  }

  @Override
  public BeaconStateAltair createFromBackingNode(TreeNode node) {
    return new BeaconStateAltairImpl(this, node);
  }

  @Override
  public MutableBeaconStateAltair createBuilder() {
    return new MutableBeaconStateAltairImpl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStateAltair createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateAltairImpl createEmptyBeaconStateImpl() {
    return new BeaconStateAltairImpl(this);
  }
}
