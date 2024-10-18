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

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class PayloadAttributesV4 extends PayloadAttributesV3 {

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 targetBlockCount;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 maximumBlobCount;

  public PayloadAttributesV4(
      final @JsonProperty("timestamp") UInt64 timestamp,
      final @JsonProperty("prevRandao") Bytes32 prevRandao,
      final @JsonProperty("suggestedFeeRecipient") Bytes20 suggestedFeeRecipient,
      final @JsonProperty("withdrawals") List<WithdrawalV1> withdrawals,
      final @JsonProperty("parentBeaconBlockRoot") Bytes32 parentBeaconBlockRoot,
      final @JsonProperty("targetBlobCount") UInt64 targetBlockCount,
      final @JsonProperty("maximumBlobCount") UInt64 maximumBlobCount) {
    super(timestamp, prevRandao, suggestedFeeRecipient, withdrawals, parentBeaconBlockRoot);

    checkNotNull(targetBlockCount, "targetBlockCount");
    checkNotNull(maximumBlobCount, "maximumBlobCount");
    this.targetBlockCount = targetBlockCount;
    this.maximumBlobCount = maximumBlobCount;
  }

  public static Optional<PayloadAttributesV4> fromInternalPayloadBuildingAttributesV4(
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    return payloadBuildingAttributes.map(
        payloadAttributes ->
            new PayloadAttributesV4(
                payloadAttributes.getTimestamp(),
                payloadAttributes.getPrevRandao(),
                payloadAttributes.getFeeRecipient(),
                getWithdrawals(payloadAttributes),
                payloadAttributes.getParentBeaconBlockRoot(),
                payloadAttributes
                    .getTargetBlobCount()
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "targetBlobCount is required for PayloadAttributesV4")),
                payloadAttributes
                    .getMaximumBlobCount()
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "maximumBlobCount is required for PayloadAttributesV4"))));
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
    final PayloadAttributesV4 that = (PayloadAttributesV4) o;
    return Objects.equals(targetBlockCount, that.targetBlockCount)
        && Objects.equals(maximumBlobCount, that.maximumBlobCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), targetBlockCount, maximumBlobCount);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("timestamp", timestamp)
        .add("prevRandao", prevRandao)
        .add("suggestedFeeRecipient", suggestedFeeRecipient)
        .add("withdrawals", withdrawals)
        .add("parentBeaconBlockRoot", parentBeaconBlockRoot)
        .add("targetBlockCount", targetBlockCount)
        .add("maximumBlobCount", maximumBlobCount)
        .toString();
  }
}
