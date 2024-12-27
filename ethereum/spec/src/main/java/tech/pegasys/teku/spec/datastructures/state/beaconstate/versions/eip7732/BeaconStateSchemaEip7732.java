/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderSchemaEip7732;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateSchemaEip7732
    extends AbstractBeaconStateSchema<BeaconStateEip7732, MutableBeaconStateEip7732> {
  public static final int LATEST_BLOCK_HASH_INDEX = 37;
  public static final int LATEST_FULL_SLOT_INDEX = 38;
  public static final int LATEST_WITHDRAWALS_ROOT_INDEX = 39;

  @VisibleForTesting
  BeaconStateSchemaEip7732(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStateEip7732", getUniqueFields(specConfig, schemaRegistry), specConfig);
  }

  private static List<SszField> getUniqueFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    final List<SszField> newFields =
        List.of(
            new SszField(
                LATEST_BLOCK_HASH_INDEX,
                BeaconStateFields.LATEST_BLOCK_HASH,
                () -> SszPrimitiveSchemas.BYTES32_SCHEMA),
            new SszField(
                LATEST_FULL_SLOT_INDEX,
                BeaconStateFields.LATEST_FULL_SLOT,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                LATEST_WITHDRAWALS_ROOT_INDEX,
                BeaconStateFields.LATEST_WITHDRAWALS_ROOT,
                () -> SszPrimitiveSchemas.BYTES32_SCHEMA));

    return Stream.concat(
            BeaconStateSchemaElectra.getUniqueFields(specConfig, schemaRegistry).stream(),
            newFields.stream())
        .toList();
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

  public ExecutionPayloadHeaderSchemaEip7732 getLastExecutionPayloadHeaderSchema() {
    return (ExecutionPayloadHeaderSchemaEip7732)
        getChildSchema(getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingDeposit, ?> getPendingDepositsSchema() {
    return (SszListSchema<PendingDeposit, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PENDING_DEPOSITS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingPartialWithdrawal, ?> getPendingPartialWithdrawalsSchema() {
    return (SszListSchema<PendingPartialWithdrawal, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingConsolidation, ?> getPendingConsolidationsSchema() {
    return (SszListSchema<PendingConsolidation, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PENDING_CONSOLIDATIONS));
  }

  @Override
  public MutableBeaconStateEip7732 createBuilder() {
    return new MutableBeaconStateEip7732Impl(createEmptyBeaconStateImpl(), true);
  }

  public static BeaconStateSchemaEip7732 create(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaEip7732(specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaEip7732 required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaEip7732,
        "Expected a BeaconStateSchemaEip7732 but was %s",
        schema.getClass());
    return (BeaconStateSchemaEip7732) schema;
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<HistoricalSummary, ?> getHistoricalSummariesSchema() {
    return (SszListSchema<HistoricalSummary, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.HISTORICAL_SUMMARIES));
  }

  @Override
  public BeaconStateEip7732 createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateEip7732Impl createEmptyBeaconStateImpl() {
    return new BeaconStateEip7732Impl(this);
  }

  @Override
  public BeaconStateEip7732Impl createFromBackingNode(final TreeNode node) {
    return new BeaconStateEip7732Impl(this, node);
  }
}
