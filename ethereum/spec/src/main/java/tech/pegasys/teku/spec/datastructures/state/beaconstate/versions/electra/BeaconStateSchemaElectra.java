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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateProfileSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingBalanceDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;

public class BeaconStateSchemaElectra
    extends AbstractBeaconStateProfileSchema<BeaconStateElectra, MutableBeaconStateElectra> {
  public static final int DEPOSIT_REQUESTS_START_INDEX = 28;
  public static final int DEPOSIT_BALANCE_TO_CONSUME_INDEX = 29;
  public static final int EXIT_BALANCE_TO_CONSUME_INDEX = 30;
  public static final int EARLIEST_EXIT_EPOCH_INDEX = 31;
  public static final int CONSOLIDATION_BALANCE_TO_CONSUME_INDEX = 32;
  public static final int EARLIEST_CONSOLIDATION_EPOCH_INDEX = 33;
  public static final int PENDING_BALANCE_DEPOSITS_INDEX = 34;
  public static final int PENDING_PARTIAL_WITHDRAWALS_INDEX = 35;
  public static final int PENDING_CONSOLIDATIONS_INDEX = 36;

  @VisibleForTesting
  BeaconStateSchemaElectra(final SpecConfig specConfig) {
    super("BeaconStateElectra", getUniqueFields(specConfig), specConfig);
  }

  private static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final HistoricalSummary.HistoricalSummarySchema historicalSummarySchema =
        new HistoricalSummary.HistoricalSummarySchema();
    final PendingBalanceDeposit.PendingBalanceDepositSchema pendingBalanceDepositSchema =
        new PendingBalanceDeposit.PendingBalanceDepositSchema();
    final PendingPartialWithdrawal.PendingPartialWithdrawalSchema pendingPartialWithdrawalSchema =
        new PendingPartialWithdrawal.PendingPartialWithdrawalSchema();
    final SpecConfigElectra specConfigElectra = SpecConfigElectra.required(specConfig);
    final PendingConsolidation.PendingConsolidationSchema pendingConsolidationSchema =
        new PendingConsolidation.PendingConsolidationSchema();
    final SszField latestExecutionPayloadHeaderField =
        new SszField(
            LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX,
            BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER,
            () -> new ExecutionPayloadHeaderSchemaElectra(specConfigElectra));
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
    final SszField depositRequestsStartIndexField =
        new SszField(
            DEPOSIT_REQUESTS_START_INDEX,
            BeaconStateFields.DEPOSIT_REQUESTS_START_INDEX,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszField depositBalanceToConsumeField =
        new SszField(
            DEPOSIT_BALANCE_TO_CONSUME_INDEX,
            BeaconStateFields.DEPOSIT_BALANCE_TO_CONSUME,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszField exitBalanceToConsumeField =
        new SszField(
            EXIT_BALANCE_TO_CONSUME_INDEX,
            BeaconStateFields.EXIT_BALANCE_TO_CONSUME,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszField earliestExitEpochField =
        new SszField(
            EARLIEST_EXIT_EPOCH_INDEX,
            BeaconStateFields.EARLIEST_EXIT_EPOCH,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszField consolidationBalanceToConsumeField =
        new SszField(
            CONSOLIDATION_BALANCE_TO_CONSUME_INDEX,
            BeaconStateFields.CONSOLIDATION_BALANCE_TO_CONSUME,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszField earliestConsolidationEpochField =
        new SszField(
            EARLIEST_CONSOLIDATION_EPOCH_INDEX,
            BeaconStateFields.EARLIEST_CONSOLIDATION_EPOCH,
            () -> SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszField pendingBalanceDepositsField =
        new SszField(
            PENDING_BALANCE_DEPOSITS_INDEX,
            BeaconStateFields.PENDING_BALANCE_DEPOSITS,
            () ->
                SszListSchema.create(
                    pendingBalanceDepositSchema,
                    specConfigElectra.getPendingBalanceDepositsLimit()));
    final SszField pendingPartialWithdrawalsField =
        new SszField(
            PENDING_PARTIAL_WITHDRAWALS_INDEX,
            BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS,
            () ->
                SszListSchema.create(
                    pendingPartialWithdrawalSchema,
                    specConfigElectra.getPendingPartialWithdrawalsLimit()));
    final SszField pendingConsolidationsField =
        new SszField(
            PENDING_CONSOLIDATIONS_INDEX,
            BeaconStateFields.PENDING_CONSOLIDATIONS,
            () ->
                SszListSchema.create(
                    pendingConsolidationSchema, specConfigElectra.getPendingConsolidationsLimit()));
    return Stream.concat(
            BeaconStateSchemaAltair.getUniqueFields(specConfig).stream(),
            Stream.of(
                latestExecutionPayloadHeaderField,
                nextWithdrawalIndexField,
                nextWithdrawalValidatorIndexField,
                historicalSummariesField,
                depositRequestsStartIndexField,
                depositBalanceToConsumeField,
                exitBalanceToConsumeField,
                earliestExitEpochField,
                consolidationBalanceToConsumeField,
                earliestConsolidationEpochField,
                pendingBalanceDepositsField,
                pendingPartialWithdrawalsField,
                pendingConsolidationsField))
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
  public BeaconStateElectraImpl createFromBackingNode(final TreeNode node) {
    return new BeaconStateElectraImpl(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingBalanceDeposit, ?> getPendingBalanceDepositsSchema() {
    return (SszListSchema<PendingBalanceDeposit, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PENDING_BALANCE_DEPOSITS));
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
}
