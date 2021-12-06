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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;

public class BeaconStateSchemaMerge
    extends AbstractBeaconStateSchema<BeaconStateMerge, MutableBeaconStateMerge> {

  private static final int LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX = 24;

  @VisibleForTesting
  BeaconStateSchemaMerge(final SpecConfig specConfig) {
    super("BeaconStateMerge", getUniqueFields(specConfig), specConfig);
  }

  public static BeaconStateSchemaMerge create(final SpecConfig specConfig) {
    return new BeaconStateSchemaMerge(specConfig);
  }

  private static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final SszField latestExecutionPayloadHeaderField =
        new SszField(
            LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX,
            BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER.name(),
            () -> new ExecutionPayloadHeaderSchema(SpecConfigMerge.required(specConfig)));
    return Stream.concat(
            BeaconStateSchemaAltair.getUniqueFields(specConfig).stream(),
            Stream.of(latestExecutionPayloadHeaderField))
        .collect(Collectors.toList());
  }

  public static BeaconStateSchemaMerge required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaMerge,
        "Expected a BeaconStateSchemaMerge but was %s",
        schema.getClass());
    return (BeaconStateSchemaMerge) schema;
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

  public ExecutionPayloadHeaderSchema getLastExecutionPayloadHeaderSchema() {
    return (ExecutionPayloadHeaderSchema)
        getChildSchema(getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER.name()));
  }

  @Override
  public BeaconStateMerge createFromBackingNode(TreeNode node) {
    return new BeaconStateMergeImpl(this, node);
  }

  @Override
  public MutableBeaconStateMerge createBuilder() {
    return new MutableBeaconStateMergeImpl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStateMerge createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateMergeImpl createEmptyBeaconStateImpl() {
    return new BeaconStateMergeImpl(this);
  }
}
