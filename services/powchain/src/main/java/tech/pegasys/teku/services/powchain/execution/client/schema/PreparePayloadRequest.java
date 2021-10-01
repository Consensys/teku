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

package tech.pegasys.teku.services.powchain.execution.client.schema;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.powchain.execution.client.serializer.Bytes20Serializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.BytesSerializer;
import tech.pegasys.teku.services.powchain.execution.client.serializer.UInt64AsHexSerializer;
import tech.pegasys.teku.ssz.type.Bytes20;

public class PreparePayloadRequest {
  @JsonSerialize(using = BytesSerializer.class)
  public final Bytes32 parentHash;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  public final UInt64 timestamp;

  @JsonSerialize(using = BytesSerializer.class)
  public final Bytes32 random;

  @JsonSerialize(using = Bytes20Serializer.class)
  public final Bytes20 feeRecipient;

  public PreparePayloadRequest(
      Bytes32 parentHash, UInt64 timestamp, Bytes32 random, Bytes20 feeRecipient) {
    this.parentHash = parentHash;
    this.timestamp = timestamp;
    this.random = random;
    this.feeRecipient = feeRecipient;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PreparePayloadRequest that = (PreparePayloadRequest) o;
    return Objects.equals(parentHash, that.parentHash)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(random, that.random)
        && Objects.equals(feeRecipient, that.feeRecipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(parentHash, timestamp, random, feeRecipient);
  }

  @Override
  public String toString() {
    return "PreparePayloadRequest{"
        + "parentHash="
        + parentHash
        + ", timestamp="
        + timestamp
        + ", random="
        + random
        + ", feeRecipient="
        + feeRecipient
        + '}';
  }
}
