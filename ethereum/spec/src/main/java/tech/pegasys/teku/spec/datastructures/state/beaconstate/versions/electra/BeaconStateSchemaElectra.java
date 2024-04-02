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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix.LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella.HISTORICAL_SUMMARIES_INDEX;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella.NEXT_WITHDRAWAL_INDEX;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella.NEXT_WITHDRAWAL_VALIDATOR_INDEX;

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
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadHeaderSchemaElectra;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;

public class BeaconStateSchemaElectra
    extends AbstractBeaconStateSchema<BeaconStateElectra, MutableBeaconStateElectra> {
  public static final int DEPOSIT_RECEIPTS_START_INDEX = 28;

  @VisibleForTesting
  BeaconStateSchemaElectra(final SpecConfig specConfig) {
    super("BeaconStateElectra", getUniqueFields(specConfig), specConfig);
  }

  private static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final HistoricalSummary.HistoricalSummarySchema historicalSummarySchema =
        new HistoricalSummary.HistoricalSummarySchema();
    final SszField latestExecutionPayloadHeaderField =
        new SszField(
            LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX,
            BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER,
            () -> new ExecutionPayloadHeaderSchemaElectra(SpecConfigElectra.required(specConfig)));
    final SszField nextWithdrawalIndexField =
        new SszField(
            NEXT_WITHDRAWAL_INDEX,
            BeaconStateFields.NEXT_WITHDRAWAL_INDEX,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszField nextWithdrawalValidatorIndexField =
        new SszField(
            NEXT_WITHDRAWAL_VALIDATOR_INDEX,
            BeaconStateFields.NEXT_WITHDRAWAL_VALIDATOR_INDEX,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);

    final SszField historicalSummariesField =
        new SszField(
            HISTORICAL_SUMMARIES_INDEX,
            BeaconStateFields.HISTORICAL_SUMMARIES,
            () ->
                SszListSchema.create(
                    historicalSummarySchema, specConfig.getHistoricalRootsLimit()));
    final SszField depositReceiptsStartIndexField =
        new SszField(
            DEPOSIT_RECEIPTS_START_INDEX,
            BeaconStateFields.DEPOSIT_RECEIPTS_START_INDEX,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);
    return Stream.concat(
            BeaconStateSchemaAltair.getUniqueFields(specConfig).stream(),
            Stream.of(
                latestExecutionPayloadHeaderField,
                nextWithdrawalIndexField,
                nextWithdrawalValidatorIndexField,
                historicalSummariesField,
                depositReceiptsStartIndexField))
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

  public ExecutionPayloadHeaderSchemaElectra getLastExecutionPayloadHeaderSchema() {
    return (ExecutionPayloadHeaderSchemaElectra)
        getChildSchema(getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER));
  }

  @Override
  public MutableBeaconStateElectra createBuilder() {
    return new MutableBeaconStateElectraImpl(createEmptyBeaconStateImpl(), true);
  }

  public static BeaconStateSchemaElectra create(final SpecConfig specConfig) {
    return new BeaconStateSchemaElectra(specConfig);
  }

  public static BeaconStateSchemaElectra required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaElectra,
        "Expected a BeaconStateSchemaElectra but was %s",
        schema.getClass());
    return (BeaconStateSchemaElectra) schema;
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<HistoricalSummary, ?> getHistoricalSummariesSchema() {
    return (SszListSchema<HistoricalSummary, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.HISTORICAL_SUMMARIES));
  }

  @Override
  public BeaconStateElectra createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateElectraImpl createEmptyBeaconStateImpl() {
    return new BeaconStateElectraImpl(this);
  }

  @Override
  public BeaconStateElectraImpl createFromBackingNode(TreeNode node) {
    return new BeaconStateElectraImpl(this, node);
  }
}
