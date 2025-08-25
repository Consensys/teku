/*
 * Copyright Consensys Software Inc., 2025
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

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateSchemaGloas
    extends AbstractBeaconStateSchema<BeaconStateGloas, MutableBeaconStateGloas> {

  @VisibleForTesting
  BeaconStateSchemaGloas(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStateGloas", getUniqueFields(specConfig, schemaRegistry), specConfig);
  }

  private static List<SszField> getUniqueFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return BeaconStateSchemaFulu.getUniqueFields(specConfig, schemaRegistry);
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
