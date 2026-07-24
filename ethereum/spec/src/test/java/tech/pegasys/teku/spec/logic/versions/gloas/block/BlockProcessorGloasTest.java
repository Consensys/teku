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

package tech.pegasys.teku.spec.logic.versions.gloas.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsSchemaGloas;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPaymentSchema;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockProcessorGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigGloas config = SpecConfigGloas.required(spec.getGenesisSpecConfig());
  private final ExecutionRequestsSchemaGloas executionRequestsSchema =
      ExecutionRequestsSchemaGloas.required(
          SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
              .getExecutionRequestsSchema());
  private final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();

  private BlockProcessorGloas blockProcessor() {
    return (BlockProcessorGloas) spec.getGenesisSpec().getBlockProcessor();
  }

  @Test
  void removeBuilderPendingPayment_clearsPaymentWhenSlashedValidatorIsThePaymentProposer() {
    // header slot in the current epoch (epoch 2)
    final UInt64 headerSlot = UInt64.valueOf(2L * slotsPerEpoch + 1);
    final UInt64 proposerIndex = UInt64.valueOf(3);
    final int paymentIndex = slotsPerEpoch + headerSlot.mod(slotsPerEpoch).intValue();

    final BeaconState state =
        stateWithPaymentAt(headerSlot, paymentIndex, paymentWithProposer(proposerIndex));
    final ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing(headerSlot, proposerIndex);

    final BeaconState result =
        state.updated(mutable -> blockProcessor().removeBuilderPendingPayment(slashing, mutable));

    assertThat(builderPaymentAt(result, paymentIndex)).isEqualTo(paymentSchema().getDefault());
  }

  @Test
  void removeBuilderPendingPayment_keepsPaymentWhenSlashedValidatorIsNotThePaymentProposer() {
    final UInt64 headerSlot = UInt64.valueOf(2L * slotsPerEpoch + 1);
    final UInt64 paymentProposer = UInt64.valueOf(3);
    final UInt64 slashedProposer = UInt64.valueOf(7);
    final int paymentIndex = slotsPerEpoch + headerSlot.mod(slotsPerEpoch).intValue();

    final BuilderPendingPayment payment = paymentWithProposer(paymentProposer);
    final BeaconState state = stateWithPaymentAt(headerSlot, paymentIndex, payment);
    final ProposerSlashing slashing =
        dataStructureUtil.randomProposerSlashing(headerSlot, slashedProposer);

    final BeaconState result =
        state.updated(mutable -> blockProcessor().removeBuilderPendingPayment(slashing, mutable));

    assertThat(builderPaymentAt(result, paymentIndex)).isEqualTo(payment);
  }

  @Test
  void applyParentExecutionPayload_rejectsTooManyBoundedRequests() {
    assertTooManyRequestsAreRejected(
        executionRequestsSchema.create(
            List.of(),
            Collections.nCopies(
                config.getMaxWithdrawalRequestsPerPayload() + 1,
                dataStructureUtil.randomWithdrawalRequest()),
            List.of(),
            List.of(),
            List.of()),
        "Too many withdrawal requests");
    assertTooManyRequestsAreRejected(
        executionRequestsSchema.create(
            List.of(),
            List.of(),
            Collections.nCopies(
                config.getMaxConsolidationRequestsPerPayload() + 1,
                dataStructureUtil.randomConsolidationRequest()),
            List.of(),
            List.of()),
        "Too many consolidation requests");
    assertTooManyRequestsAreRejected(
        executionRequestsSchema.create(
            List.of(),
            List.of(),
            List.of(),
            Collections.nCopies(
                config.getMaxBuilderDepositRequestsPerPayload() + 1,
                dataStructureUtil.randomBuilderDepositRequest()),
            List.of()),
        "Too many builder deposit requests");
    assertTooManyRequestsAreRejected(
        executionRequestsSchema.create(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Collections.nCopies(
                config.getMaxBuilderExitRequestsPerPayload() + 1,
                dataStructureUtil.randomBuilderExitRequest())),
        "Too many builder exit requests");
  }

  @Test
  void processOperationsNoValidation_rejectsTooManyBoundedOperations() {
    final BeaconBlockBodyGloas tooManyProposerSlashings = emptyBody();
    when(tooManyProposerSlashings.getProposerSlashings())
        .thenReturn(sszList(config.getMaxProposerSlashings() + 1));
    assertTooManyOperationsAreRejected(tooManyProposerSlashings, "Too many proposer slashings");

    final BeaconBlockBodyGloas tooManyAttesterSlashings = emptyBody();
    when(tooManyAttesterSlashings.getAttesterSlashings())
        .thenReturn(sszList(config.getMaxAttesterSlashingsElectra() + 1));
    assertTooManyOperationsAreRejected(tooManyAttesterSlashings, "Too many attester slashings");

    final BeaconBlockBodyGloas tooManyAttestations = emptyBody();
    when(tooManyAttestations.getAttestations())
        .thenReturn(sszList(config.getMaxAttestationsElectra() + 1));
    assertTooManyOperationsAreRejected(tooManyAttestations, "Too many attestations");

    final BeaconBlockBodyGloas tooManyVoluntaryExits = emptyBody();
    when(tooManyVoluntaryExits.getVoluntaryExits())
        .thenReturn(sszList(config.getMaxVoluntaryExits() + 1));
    assertTooManyOperationsAreRejected(tooManyVoluntaryExits, "Too many voluntary exits");

    final BeaconBlockBodyGloas tooManyBlsToExecutionChanges = emptyBody();
    when(tooManyBlsToExecutionChanges.getBlsToExecutionChanges())
        .thenReturn(sszList(config.getMaxBlsToExecutionChanges() + 1));
    assertTooManyOperationsAreRejected(
        tooManyBlsToExecutionChanges, "Too many BLS to execution changes");

    final BeaconBlockBodyGloas tooManyPayloadAttestations = emptyBody();
    when(tooManyPayloadAttestations.getPayloadAttestations())
        .thenReturn(sszList(config.getMaxPayloadAttestations() + 1));
    assertTooManyOperationsAreRejected(tooManyPayloadAttestations, "Too many payload attestations");
  }

  private void assertTooManyRequestsAreRejected(
      final ExecutionRequests requests, final String expectedMessage) {
    assertThatThrownBy(() -> blockProcessor().applyParentExecutionPayload(null, requests, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(expectedMessage);
  }

  private void assertTooManyOperationsAreRejected(
      final BeaconBlockBodyGloas body, final String expectedMessage) {
    assertThatThrownBy(() -> blockProcessor().processOperationsNoValidation(null, body, null, null))
        .isInstanceOf(BlockProcessingException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(expectedMessage);
  }

  private BeaconBlockBodyGloas emptyBody() {
    final BeaconBlockBodyGloas body = mock(BeaconBlockBodyGloas.class);
    when(body.toVersionGloas()).thenReturn(Optional.of(body));
    when(body.getProposerSlashings()).thenReturn(sszList(0));
    when(body.getAttesterSlashings()).thenReturn(sszList(0));
    when(body.getAttestations()).thenReturn(sszList(0));
    when(body.getVoluntaryExits()).thenReturn(sszList(0));
    when(body.getBlsToExecutionChanges()).thenReturn(sszList(0));
    when(body.getPayloadAttestations()).thenReturn(sszList(0));
    return body;
  }

  @SuppressWarnings("unchecked")
  private static <T extends SszData> SszList<T> sszList(final int size) {
    return mock(
        SszList.class,
        invocation ->
            invocation.getMethod().getName().equals("size")
                ? size
                : Answers.RETURNS_DEFAULTS.answer(invocation));
  }

  private BeaconState stateWithPaymentAt(
      final UInt64 slot, final int paymentIndex, final BuilderPendingPayment payment) {
    return dataStructureUtil
        .randomBeaconState(slot)
        .updated(
            mutable ->
                MutableBeaconStateGloas.required(mutable)
                    .getBuilderPendingPayments()
                    .set(paymentIndex, payment));
  }

  private BuilderPendingPayment paymentWithProposer(final UInt64 proposerIndex) {
    return paymentSchema()
        .create(
            UInt64.valueOf(1000),
            dataStructureUtil.randomBuilderPendingWithdrawal(),
            proposerIndex);
  }

  private BuilderPendingPayment builderPaymentAt(final BeaconState state, final int index) {
    return BeaconStateGloas.required(state).getBuilderPendingPayments().get(index);
  }

  private BuilderPendingPaymentSchema paymentSchema() {
    return spec.getGenesisSchemaDefinitions()
        .toVersionGloas()
        .orElseThrow()
        .getBuilderPendingPaymentSchema();
  }
}
