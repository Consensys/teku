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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip4844;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix.LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX;

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
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.ExecutionPayloadHeaderSchemaEip4844;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;

public class BeaconStateSchemaEip4844
    extends AbstractBeaconStateSchema<BeaconStateEip4844, MutableBeaconStateEip4844> {

  @VisibleForTesting
  BeaconStateSchemaEip4844(final SpecConfig specConfig) {
    super("BeaconStateEip4844", getUniqueFields(specConfig), specConfig);
  }

  private static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final SszField latestExecutionPayloadHeaderField =
        new SszField(
            LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX,
            BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER,
            () -> new ExecutionPayloadHeaderSchemaEip4844(SpecConfigEip4844.required(specConfig)));
    return Stream.concat(
            BeaconStateSchemaAltair.getUniqueFields(specConfig).stream(),
            Stream.of(latestExecutionPayloadHeaderField))
        .collect(Collectors.toList());
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

  public SyncCommittee.SyncCommitteeSchema getCurrentSyncCommitteeSchema() {
    return (SyncCommittee.SyncCommitteeSchema)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE));
  }

  public SyncCommittee.SyncCommitteeSchema getNextSyncCommitteeSchema() {
    return (SyncCommittee.SyncCommitteeSchema)
        getChildSchema(getFieldIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE));
  }

  public ExecutionPayloadHeaderSchemaEip4844 getLastExecutionPayloadHeaderSchema() {
    return (ExecutionPayloadHeaderSchemaEip4844)
        getChildSchema(getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER));
  }

  @Override
  public MutableBeaconStateEip4844 createBuilder() {
    return new MutableBeaconStateEip4844Impl(createEmptyBeaconStateImpl(), true);
  }

  public static BeaconStateSchemaEip4844 create(final SpecConfig specConfig) {
    return new BeaconStateSchemaEip4844(specConfig);
  }

  public static BeaconStateSchemaEip4844 required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaEip4844,
        "Expected a BeaconStateSchemaEip4844 but was %s",
        schema.getClass());
    return (BeaconStateSchemaEip4844) schema;
  }

  @Override
  public BeaconStateEip4844 createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateEip4844Impl createEmptyBeaconStateImpl() {
    return new BeaconStateEip4844Impl(this);
  }

  @Override
  public BeaconStateEip4844 createFromBackingNode(TreeNode node) {
    return new BeaconStateEip4844Impl(this, node);
  }
}
