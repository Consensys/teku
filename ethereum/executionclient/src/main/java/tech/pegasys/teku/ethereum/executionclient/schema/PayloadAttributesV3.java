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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class PayloadAttributesV3 extends PayloadAttributesV2 {

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 parentBeaconBlockRoot;

  public PayloadAttributesV3(
      final @JsonProperty("timestamp") UInt64 timestamp,
      final @JsonProperty("prevRandao") Bytes32 prevRandao,
      final @JsonProperty("suggestedFeeRecipient") Bytes20 suggestedFeeRecipient,
      final @JsonProperty("withdrawals") List<WithdrawalV1> withdrawals,
      @JsonProperty("parentBeaconBlockRoot") final Bytes32 parentBeaconBlockRoot) {
    super(timestamp, prevRandao, suggestedFeeRecipient, withdrawals);
    this.parentBeaconBlockRoot = parentBeaconBlockRoot;
  }

  public static Optional<PayloadAttributesV3> fromInternalPayloadBuildingAttributesV3(
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    return payloadBuildingAttributes.map(
        (payloadAttributes) ->
            new PayloadAttributesV3(
                payloadAttributes.getTimestamp(),
                payloadAttributes.getPrevRandao(),
                payloadAttributes.getFeeRecipient(),
                getWithdrawals(
                    payloadAttributes.getWithdrawals(), payloadAttributes.getProposalSlot()),
                payloadAttributes.getParentBeaconBlockRoot()));
  }
}
