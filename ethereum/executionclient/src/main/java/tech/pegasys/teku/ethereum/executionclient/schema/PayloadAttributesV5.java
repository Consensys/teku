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

package tech.pegasys.teku.ethereum.executionclient.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class PayloadAttributesV5 extends PayloadAttributesV4 {

  public final List<String> inclusionListTransactions;

  public PayloadAttributesV5(
      final @JsonProperty("timestamp") UInt64 timestamp,
      final @JsonProperty("prevRandao") Bytes32 prevRandao,
      final @JsonProperty("suggestedFeeRecipient") Bytes20 suggestedFeeRecipient,
      final @JsonProperty("withdrawals") List<WithdrawalV1> withdrawals,
      final @JsonProperty("parentBeaconBlockRoot") Bytes32 parentBeaconBlockRoot,
      final @JsonProperty("slotNumber") UInt64 slotNumber,
      final @JsonProperty("inclusionListTransactions") List<String> inclusionListTransactions) {
    super(
        timestamp,
        prevRandao,
        suggestedFeeRecipient,
        withdrawals,
        parentBeaconBlockRoot,
        slotNumber);
    this.inclusionListTransactions = inclusionListTransactions;
  }

  public static PayloadAttributesV5 fromInternalPayloadBuildingAttributesV5(
      final PayloadBuildingAttributes payloadBuildingAttributes) {
    final List<String> inclusionListTransactionHexes =
        payloadBuildingAttributes.getInclusionListTransactions().stream()
            .map(Bytes::toHexString)
            .collect(Collectors.toList());
    return new PayloadAttributesV5(
        payloadBuildingAttributes.getTimestamp(),
        payloadBuildingAttributes.getPrevRandao(),
        payloadBuildingAttributes.getFeeRecipient(),
        getWithdrawals(payloadBuildingAttributes),
        payloadBuildingAttributes.getParentBeaconBlockRoot(),
        payloadBuildingAttributes.getProposalSlot(),
        inclusionListTransactionHexes);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final PayloadAttributesV5 that = (PayloadAttributesV5) o;
    return Objects.equals(inclusionListTransactions, that.inclusionListTransactions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), inclusionListTransactions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("timestamp", timestamp)
        .add("prevRandao", prevRandao)
        .add("suggestedFeeRecipient", suggestedFeeRecipient)
        .add("withdrawals", withdrawals)
        .add("parentBeaconBlockRoot", parentBeaconBlockRoot)
        .add("slotNumber", slotNumber)
        .add("inclusionListTransactions", inclusionListTransactions)
        .toString();
  }
}
