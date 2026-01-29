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

package tech.pegasys.teku.statetransition.payloadattestation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MatchingDataPayloadAttestationGroupTest {

  private final Spec spec = TestSpecFactory.createMainnetGloas();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final PayloadAttestationData data = dataStructureUtil.randomPayloadAttestationData();

  private final PayloadAttestationMessageSchema payloadAttestationMessageSchema =
      SchemaDefinitionsGloas.required(spec.atSlot(data.getSlot()).getSchemaDefinitions())
          .getPayloadAttestationMessageSchema();

  private final MatchingDataPayloadAttestationGroup payloadAttestationGroup =
      new MatchingDataPayloadAttestationGroup(spec, data);

  @Test
  public void doesNotAddPayloadAttestationMessageIfDataDoesNotMatch() {
    assertThat(payloadAttestationGroup.add(dataStructureUtil.randomPayloadAttestationMessage()))
        .isFalse();
  }

  @Test
  public void doesNotAddPayloadAttestationMessageIfValidatorIndexHasAlreadyBeenAdded() {
    final PayloadAttestationMessage message =
        payloadAttestationMessageSchema.create(
            UInt64.ONE, data, dataStructureUtil.randomSignature());

    assertThat(payloadAttestationGroup.add(message)).isTrue();
    assertThat(payloadAttestationGroup.size()).isOne();
    assertThat(payloadAttestationGroup.add(message)).isFalse();
    assertThat(payloadAttestationGroup.size()).isOne();
  }

  @Test
  public void failsIfCreatingAggregatedPayloadAttestationWhenNoAttestationsAdded() {
    assertThatThrownBy(
            () -> payloadAttestationGroup.createAggregatedPayloadAttestation(IntList.of(1, 2, 3)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Nothing to aggregate");
  }

  @Test
  public void createsAggregatedPayloadAttestationFromMultipleAttestations() {
    final IntList ptc = IntList.of(42, 1, 9, 3, 56, 2);

    final PayloadAttestationMessage message1 =
        payloadAttestationMessageSchema.create(
            UInt64.ONE, data, dataStructureUtil.randomSignature());
    final PayloadAttestationMessage message2 =
        payloadAttestationMessageSchema.create(
            UInt64.valueOf(2), data, dataStructureUtil.randomSignature());
    final PayloadAttestationMessage message3 =
        payloadAttestationMessageSchema.create(
            UInt64.valueOf(3), data, dataStructureUtil.randomSignature());

    payloadAttestationGroup.add(message1);
    payloadAttestationGroup.add(message2);
    payloadAttestationGroup.add(message3);

    final PayloadAttestation aggregatedPayloadAttestation =
        payloadAttestationGroup.createAggregatedPayloadAttestation(ptc);

    // assert that aggregation bits are set correctly relative to the PTC position
    assertThat(aggregatedPayloadAttestation.getAggregationBits().streamAllSetBits())
        .containsExactly(1, 3, 5);
    assertThat(aggregatedPayloadAttestation.getData()).isEqualTo(data);
  }

  @Test
  public void createsAggregatedPayloadAttestationWhenPtcHasSameValidatorMultipleTimes() {
    final IntList ptc = IntList.of(42, 1, 42, 42, 3);

    final PayloadAttestationMessage message1 =
        payloadAttestationMessageSchema.create(
            UInt64.valueOf(42), data, dataStructureUtil.randomSignature());
    final PayloadAttestationMessage message2 =
        payloadAttestationMessageSchema.create(
            UInt64.valueOf(3), data, dataStructureUtil.randomSignature());

    payloadAttestationGroup.add(message1);
    payloadAttestationGroup.add(message2);

    final PayloadAttestation aggregatedPayloadAttestation =
        payloadAttestationGroup.createAggregatedPayloadAttestation(ptc);

    assertThat(aggregatedPayloadAttestation.getAggregationBits().streamAllSetBits())
        .containsExactly(0, 2, 3, 4);
  }
}
