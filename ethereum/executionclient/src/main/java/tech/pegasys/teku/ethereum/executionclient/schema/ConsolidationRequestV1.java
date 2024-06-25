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

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes48Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;

public class ConsolidationRequestV1 {
  @JsonSerialize(using = Bytes20Serializer.class)
  @JsonDeserialize(using = Bytes20Deserializer.class)
  public final Bytes20 sourceAddress;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes48Deserializer.class)
  public final Bytes48 sourcePubkey;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes48Deserializer.class)
  public final Bytes48 targetPubkey;

  public ConsolidationRequestV1(
      @JsonProperty("sourceAddress") final Bytes20 sourceAddress,
      @JsonProperty("sourcePubkey") final Bytes48 sourcePubkey,
      @JsonProperty("targetPubkey") final Bytes48 targetPubkey) {
    checkNotNull(sourceAddress, "sourceAddress");
    checkNotNull(sourcePubkey, "sourcePubkey");
    checkNotNull(targetPubkey, "targetPubkey");
    this.sourceAddress = sourceAddress;
    this.sourcePubkey = sourcePubkey;
    this.targetPubkey = targetPubkey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ConsolidationRequestV1 that = (ConsolidationRequestV1) o;
    return Objects.equals(sourceAddress, that.sourceAddress)
        && Objects.equals(sourcePubkey, that.sourcePubkey)
        && Objects.equals(targetPubkey, that.targetPubkey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, sourceAddress, targetPubkey);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("sourceAddress", sourceAddress)
        .add("sourcePubkey", sourcePubkey)
        .add("targetPubkey", targetPubkey)
        .toString();
  }
}
