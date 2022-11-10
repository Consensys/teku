/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix;

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
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadHeaderSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;

public class BeaconStateSchemaBellatrix
    extends AbstractBeaconStateSchema<BeaconStateBellatrix, MutableBeaconStateBellatrix> {

  public static final int LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX = 24;

  @VisibleForTesting
  BeaconStateSchemaBellatrix(final SpecConfig specConfig) {
    super("BeaconStateBellatrix", getUniqueFields(specConfig), specConfig);
  }

  public static BeaconStateSchemaBellatrix create(final SpecConfig specConfig) {
    return new BeaconStateSchemaBellatrix(specConfig);
  }

  public static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final SszField latestExecutionPayloadHeaderField =
        new SszField(
            LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX,
            BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER,
            () ->
                new ExecutionPayloadHeaderSchemaBellatrix(
                    SpecConfigBellatrix.required(specConfig)));
    return Stream.concat(
            BeaconStateSchemaAltair.getUniqueFields(specConfig).stream(),
            Stream.of(latestExecutionPayloadHeaderField))
        .collect(Collectors.toList());
  }

  public static BeaconStateSchemaBellatrix required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaBellatrix,
        "Expected a BeaconStateSchemaBellatrix but was %s",
        schema.getClass());
    return (BeaconStateSchemaBellatrix) schema;
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<Byte, SszByte, ?> getPreviousEpochParticipationSchema() {
    return (SszPrimitiveListSchema<Byte, SszByte, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION));
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<Byte, SszByte, ?> getCurrentEpochParticipationSchema() {
    return (SszPrimitiveListSchema<Byte, SszByte, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION));
  }

  public SszUInt64ListSchema<?> getInactivityScoresSchema() {
    return (SszUInt64ListSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.INACTIVITY_SCORES));
  }

  public SyncCommitteeSchema getCurrentSyncCommitteeSchema() {
    return (SyncCommitteeSchema)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE));
  }

  public SyncCommitteeSchema getNextSyncCommitteeSchema() {
    return (SyncCommitteeSchema)
        getChildSchema(getFieldIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE));
  }

  public ExecutionPayloadHeaderSchemaBellatrix getLastExecutionPayloadHeaderSchema() {
    return (ExecutionPayloadHeaderSchemaBellatrix)
        getChildSchema(getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER));
  }

  @Override
  public BeaconStateBellatrix createFromBackingNode(TreeNode node) {
    return new BeaconStateBellatrixImpl(this, node);
  }

  @Override
  public MutableBeaconStateBellatrix createBuilder() {
    return new MutableBeaconStateBellatrixImpl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStateBellatrix createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateBellatrixImpl createEmptyBeaconStateImpl() {
    return new BeaconStateBellatrixImpl(this);
  }
}
