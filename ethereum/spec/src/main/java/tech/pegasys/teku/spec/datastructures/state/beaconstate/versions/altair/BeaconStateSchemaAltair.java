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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;

public class BeaconStateSchemaAltair
    extends AbstractBeaconStateSchema<BeaconStateAltair, MutableBeaconStateAltair> {

  private static final int PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX = 15;
  private static final int CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX = 16;
  private static final int INACTIVITY_SCORES_FIELD_INDEX = 21;
  private static final int CURRENT_SYNC_COMMITTEE_FIELD_INDEX = 22;
  private static final int NEXT_SYNC_COMMITTEE_FIELD_INDEX = 23;

  @VisibleForTesting
  BeaconStateSchemaAltair(final SpecConfig specConfig) {
    super("BeaconStateAltair", getUniqueFields(specConfig), specConfig);
  }

  public static BeaconStateSchemaAltair create(final SpecConfig specConfig) {
    return new BeaconStateSchemaAltair(specConfig);
  }

  public static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final SszField previousEpochAttestationsField =
        new SszField(
            PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX,
            BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION.name(),
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.BYTE_SCHEMA, specConfig.getValidatorRegistryLimit()));
    final SszField currentEpochAttestationsField =
        new SszField(
            CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX,
            BeaconStateFields.CURRENT_EPOCH_PARTICIPATION.name(),
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.BYTE_SCHEMA, specConfig.getValidatorRegistryLimit()));

    final SszField inactivityScores =
        new SszField(
            INACTIVITY_SCORES_FIELD_INDEX,
            BeaconStateFields.INACTIVITY_SCORES.name(),
            SszUInt64ListSchema.create(specConfig.getValidatorRegistryLimit()));
    final SszField currentSyncCommitteeField =
        new SszField(
            CURRENT_SYNC_COMMITTEE_FIELD_INDEX,
            BeaconStateFields.CURRENT_SYNC_COMMITTEE.name(),
            () -> new SyncCommitteeSchema(SpecConfigAltair.required(specConfig)));
    final SszField nextSyncCommitteeField =
        new SszField(
            NEXT_SYNC_COMMITTEE_FIELD_INDEX,
            BeaconStateFields.NEXT_SYNC_COMMITTEE.name(),
            () -> new SyncCommitteeSchema(SpecConfigAltair.required(specConfig)));
    return List.of(
        previousEpochAttestationsField,
        currentEpochAttestationsField,
        inactivityScores,
        currentSyncCommitteeField,
        nextSyncCommitteeField);
  }

  public static BeaconStateSchemaAltair required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaAltair,
        "Expected a BeaconStateSchemaAltair but was %s",
        schema.getClass());
    return (BeaconStateSchemaAltair) schema;
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<Byte, SszByte, ?> getPreviousEpochParticipationSchema() {
    return (SszPrimitiveListSchema<Byte, SszByte, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION.name()));
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<Byte, SszByte, ?> getCurrentEpochParticipationSchema() {
    return (SszPrimitiveListSchema<Byte, SszByte, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION.name()));
  }

  public SszUInt64ListSchema<?> getInactivityScoresSchema() {
    return (SszUInt64ListSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.INACTIVITY_SCORES.name()));
  }

  public SyncCommitteeSchema getCurrentSyncCommitteeSchema() {
    return (SyncCommitteeSchema)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE.name()));
  }

  public SyncCommitteeSchema getNextSyncCommitteeSchema() {
    return (SyncCommitteeSchema)
        getChildSchema(getFieldIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE.name()));
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
