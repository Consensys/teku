/*
 * Copyright ConsenSys Software Inc., 2022
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class PayloadAttributesV2 extends PayloadAttributesV1 {

  public final List<WithdrawalV1> withdrawals;

  public PayloadAttributesV2(
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("prevRandao") Bytes32 prevRandao,
      @JsonProperty("suggestedFeeRecipient") Bytes20 suggestedFeeRecipient,
      @JsonProperty("withdrawals") final List<WithdrawalV1> withdrawals) {
    super(timestamp, prevRandao, suggestedFeeRecipient);
    this.withdrawals = withdrawals;
  }

  public static Optional<PayloadAttributesV1> fromInternalPayloadBuildingAttributesV2(
      Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    return payloadBuildingAttributes.map(
        (payloadAttributes) ->
            new PayloadAttributesV2(
                payloadAttributes.getTimestamp(),
                payloadAttributes.getPrevRandao(),
                payloadAttributes.getFeeRecipient(),
                PayloadAttributesV2.getWithdrawals(payloadAttributes.getWithdrawals())));
  }

  public static List<WithdrawalV1> getWithdrawals(
      final Optional<List<Withdrawal>> maybeWithdrawals) {
    if (maybeWithdrawals.isEmpty()) {
      return null;
    }

    final List<WithdrawalV1> withdrawals = new ArrayList<>();

    for (Withdrawal w : maybeWithdrawals.get()) {
      withdrawals.add(
          new WithdrawalV1(w.getIndex(), w.getValidatorIndex(), w.getAddress(), w.getAmount()));
    }
    return withdrawals;
  }
}
