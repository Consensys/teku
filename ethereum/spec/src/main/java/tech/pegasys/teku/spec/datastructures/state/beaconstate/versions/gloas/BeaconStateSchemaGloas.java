/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix.LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_PAYMENTS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PENDING_WITHDRAWALS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_AVAILABILITY_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_BID_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_SCHEMA;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateSchemaGloas
    extends AbstractBeaconStateSchema<BeaconStateGloas, MutableBeaconStateGloas> {

  public static final int BUILDERS_FIELD_INDEX = 38;
  public static final int NEXT_WITHDRAWAL_BUILDER_INDEX_FIELD_INDEX = 39;
  public static final int EXECUTION_PAYLOAD_AVAILABILITY_FIELD_INDEX = 40;
  public static final int BUILDER_PENDING_PAYMENTS_FIELD_INDEX = 41;
  public static final int BUILDER_PENDING_WITHDRAWALS_FIELD_INDEX = 42;
  public static final int LATEST_BLOCK_HASH_FIELD_INDEX = 43;
  public static final int PAYLOAD_EXPECTED_WITHDRAWALS_FIELD_INDEX = 44;

  @VisibleForTesting
  BeaconStateSchemaGloas(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStateGloas", getUniqueFields(specConfig, schemaRegistry), specConfig);
  }

  private static List<SszField> getUniqueFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    final List<SszField> newFields =
        List.of(
            new SszField(
                BUILDERS_FIELD_INDEX,
                BeaconStateFields.BUILDERS,
                () ->
                    SszListSchema.create(
                        Builder.SSZ_SCHEMA,
                        SpecConfigGloas.required(specConfig).getBuilderRegistryLimit())),
            new SszField(
                NEXT_WITHDRAWAL_BUILDER_INDEX_FIELD_INDEX,
                BeaconStateFields.NEXT_WITHDRAWAL_BUILDER_INDEX,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                EXECUTION_PAYLOAD_AVAILABILITY_FIELD_INDEX,
                BeaconStateFields.EXECUTION_PAYLOAD_AVAILABILITY,
                () -> schemaRegistry.get(EXECUTION_PAYLOAD_AVAILABILITY_SCHEMA)),
            new SszField(
                BUILDER_PENDING_PAYMENTS_FIELD_INDEX,
                BeaconStateFields.BUILDER_PENDING_PAYMENTS,
                () -> schemaRegistry.get(BUILDER_PENDING_PAYMENTS_SCHEMA)),
            new SszField(
                BUILDER_PENDING_WITHDRAWALS_FIELD_INDEX,
                BeaconStateFields.BUILDER_PENDING_WITHDRAWALS,
                () -> schemaRegistry.get(BUILDER_PENDING_WITHDRAWALS_SCHEMA)),
            new SszField(
                LATEST_BLOCK_HASH_FIELD_INDEX,
                BeaconStateFields.LATEST_BLOCK_HASH,
                () -> SszPrimitiveSchemas.BYTES32_SCHEMA),
            new SszField(
                PAYLOAD_EXPECTED_WITHDRAWALS_FIELD_INDEX,
                BeaconStateFields.PAYLOAD_EXPECTED_WITHDRAWALS,
                () -> schemaRegistry.get(EXECUTION_PAYLOAD_SCHEMA).getWithdrawalsSchemaRequired()));

    return Stream.concat(
            BeaconStateSchemaFulu.getUniqueFields(specConfig, schemaRegistry).stream()
                .map(
                    field -> {
                      // replacing the old `latest_execution_payload_header` with the new
                      // `latest_execution_payload_bid`
                      if (field.getIndex() == LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX) {
                        return new SszField(
                            LATEST_EXECUTION_PAYLOAD_HEADER_FIELD_INDEX,
                            BeaconStateFields.LATEST_EXECUTION_PAYLOAD_BID,
                            () -> schemaRegistry.get(EXECUTION_PAYLOAD_BID_SCHEMA));
                      } else {
                        return field;
                      }
                    }),
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

  public SszUInt64VectorSchema<?> getProposerLookaheadSchema() {
    return (SszUInt64VectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PROPOSER_LOOKAHEAD));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Builder, ?> getBuildersSchema() {
    return (SszListSchema<Builder, ?>) getChildSchema(getFieldIndex(BeaconStateFields.BUILDERS));
  }

  public SszBitvectorSchema<?> getExecutionPayloadAvailabilitySchema() {
    return (SszBitvectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.EXECUTION_PAYLOAD_AVAILABILITY));
  }

  @SuppressWarnings("unchecked")
  public SszVectorSchema<BuilderPendingPayment, ?> getBuilderPendingPaymentsSchema() {
    return (SszVectorSchema<BuilderPendingPayment, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.BUILDER_PENDING_PAYMENTS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderPendingWithdrawal, ?> getBuilderPendingWithdrawalsSchema() {
    return (SszListSchema<BuilderPendingWithdrawal, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.BUILDER_PENDING_WITHDRAWALS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Withdrawal, ?> getPayloadExpectedWithdrawalsSchema() {
    return (SszListSchema<Withdrawal, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PAYLOAD_EXPECTED_WITHDRAWALS));
  }

  @Override
  public MutableBeaconStateGloas createBuilder() {
    return new MutableBeaconStateGloasImpl(createEmptyBeaconStateImpl(), true);
  }

  public static BeaconStateSchemaGloas create(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaGloas(specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaGloas required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaGloas,
        "Expected a BeaconStateSchemaGloas but was %s",
        schema.getClass());
    return (BeaconStateSchemaGloas) schema;
  }

  @Override
  public BeaconStateGloas createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateGloasImpl createEmptyBeaconStateImpl() {
    return new BeaconStateGloasImpl(this);
  }

  @Override
  public BeaconStateGloasImpl createFromBackingNode(final TreeNode node) {
    return new BeaconStateGloasImpl(this, node);
  }
}
