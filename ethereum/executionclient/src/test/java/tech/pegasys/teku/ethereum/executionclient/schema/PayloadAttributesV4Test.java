/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PayloadAttributesV4Test {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void buildFromInternalPayload_RequiresTargetBlobCount() {
    final PayloadBuildingAttributes pbaMissingTargetBlobCount =
        new PayloadBuildingAttributes(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            Optional.empty(),
            Optional.empty(),
            dataStructureUtil.randomBytes32(),
            Optional.empty(),
            Optional.of(dataStructureUtil.randomUInt64()));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PayloadAttributesV4.fromInternalPayloadBuildingAttributesV4(
                Optional.of(pbaMissingTargetBlobCount)));
  }

  @Test
  public void buildFromInternalPayload_RequiresMaximumBlobCount() {
    final PayloadBuildingAttributes pbaMissingMaximumBlobCount =
        new PayloadBuildingAttributes(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            Optional.empty(),
            Optional.empty(),
            dataStructureUtil.randomBytes32(),
            Optional.of(dataStructureUtil.randomUInt64()),
            Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PayloadAttributesV4.fromInternalPayloadBuildingAttributesV4(
                Optional.of(pbaMissingMaximumBlobCount)));
  }

  @Test
  public void buildFromInternalPayload_HasCorrectValues() {
    final PayloadBuildingAttributes payloadBuildingAttributes =
        dataStructureUtil.randomPayloadBuildingAttributes(false);

    final PayloadAttributesV4 payloadAttributesV4 =
        PayloadAttributesV4.fromInternalPayloadBuildingAttributesV4(
                Optional.of(payloadBuildingAttributes))
            .orElseThrow();

    assertThat(payloadBuildingAttributes.getTimestamp()).isEqualTo(payloadAttributesV4.timestamp);
    assertThat(payloadBuildingAttributes.getPrevRandao()).isEqualTo(payloadAttributesV4.prevRandao);
    assertThat(payloadBuildingAttributes.getFeeRecipient())
        .isEqualTo(payloadAttributesV4.suggestedFeeRecipient);
    assertThat(payloadBuildingAttributes.getWithdrawals())
        .hasValueSatisfying(
            withdrawals ->
                assertEquals(withdrawals.size(), payloadAttributesV4.withdrawals.size()));
    assertThat(payloadBuildingAttributes.getParentBeaconBlockRoot())
        .isEqualTo(payloadAttributesV4.parentBeaconBlockRoot);
    assertThat(payloadBuildingAttributes.getTargetBlobCount())
        .hasValue(payloadAttributesV4.targetBlockCount);
    assertThat(payloadBuildingAttributes.getMaximumBlobCount())
        .hasValue(payloadAttributesV4.maximumBlobCount);
  }
}
