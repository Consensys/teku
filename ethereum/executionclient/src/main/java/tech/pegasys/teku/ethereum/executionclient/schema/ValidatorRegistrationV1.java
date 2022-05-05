/*
 * Copyright 2022 ConsenSys AG.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSPubKeyDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSPubKeySerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorRegistrationV1 {
  @JsonSerialize(using = Bytes20Serializer.class)
  @JsonDeserialize(using = Bytes20Deserializer.class)
  private final Bytes20 feeRecipient;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  private final UInt64 gasTarget;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  private final UInt64 timestamp;

  @JsonSerialize(using = BLSPubKeySerializer.class)
  @JsonDeserialize(using = BLSPubKeyDeserializer.class)
  private final BLSPubKey pubkey;

  @JsonCreator
  public ValidatorRegistrationV1(
      @JsonProperty("feeRecipient") Bytes20 feeRecipient,
      @JsonProperty("gasTarget") UInt64 gasTarget,
      @JsonProperty("timestamp") UInt64 timestamp,
      @JsonProperty("pubkey") BLSPubKey pubkey) {
    checkNotNull(feeRecipient, "feeRecipient cannot be null");
    checkNotNull(gasTarget, "gasTarget cannot be null");
    checkNotNull(timestamp, "timestamp cannot be null");
    checkNotNull(pubkey, "pubkey cannot be null");

    this.feeRecipient = feeRecipient;
    this.gasTarget = gasTarget;
    this.timestamp = timestamp;
    this.pubkey = pubkey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorRegistrationV1 that = (ValidatorRegistrationV1) o;
    return Objects.equals(feeRecipient, that.feeRecipient)
        && Objects.equals(gasTarget, that.gasTarget)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(pubkey, that.pubkey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(feeRecipient, gasTarget, timestamp, pubkey);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("feeRecipient", feeRecipient)
        .add("gasTarget", gasTarget)
        .add("timestamp", timestamp)
        .add("pubkey", pubkey)
        .toString();
  }
}
