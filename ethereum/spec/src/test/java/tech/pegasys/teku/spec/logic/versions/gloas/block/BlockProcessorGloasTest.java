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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPaymentSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockProcessorGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
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
