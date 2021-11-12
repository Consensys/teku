/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.services.executionengine.client.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.executionengine.client.serialization.Bytes20Deserializer;
import tech.pegasys.teku.services.executionengine.client.serialization.Bytes20Serializer;
import tech.pegasys.teku.services.executionengine.client.serialization.Bytes32Deserializer;
import tech.pegasys.teku.services.executionengine.client.serialization.BytesSerializer;
import tech.pegasys.teku.services.executionengine.client.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.services.executionengine.client.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.ssz.type.Bytes20;

public class PayloadAttributesV1 {
  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  private final UInt64 timestamp;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 random;

  @JsonSerialize(using = Bytes20Serializer.class)
  @JsonDeserialize(using = Bytes20Deserializer.class)
  private final Bytes20 feeRecipient;

  public PayloadAttributesV1(
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("random") Bytes32 random,
      @JsonProperty("feeRecipient") Bytes20 feeRecipient) {
    checkNotNull(timestamp, "timestamp");
    checkNotNull(random, "random");
    checkNotNull(feeRecipient, "feeRecipient");
    this.timestamp = timestamp;
    this.random = random;
    this.feeRecipient = feeRecipient;
  }

  public static Optional<PayloadAttributesV1> fromInternalForkChoiceState(
      Optional<PayloadAttributes> maybePayloadAttributes) {
    return maybePayloadAttributes.map(
        (payloadAttributes) ->
            new PayloadAttributesV1(
                payloadAttributes.getTimestamp(),
                payloadAttributes.getRandom(),
                payloadAttributes.getFeeRecipient()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PayloadAttributesV1 that = (PayloadAttributesV1) o;
    return Objects.equals(timestamp, that.timestamp)
        && Objects.equals(random, that.random)
        && Objects.equals(feeRecipient, that.feeRecipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, random, feeRecipient);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("timestamp", timestamp)
        .add("random", random)
        .add("feeRecipient", feeRecipient)
        .toString();
  }
}
