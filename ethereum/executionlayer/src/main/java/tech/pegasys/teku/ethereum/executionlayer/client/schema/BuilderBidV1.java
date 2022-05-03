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

package tech.pegasys.teku.ethereum.executionlayer.client.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BLSPubKeyDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.BLSPubKeySerializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt256AsHexSerializer;

public class BuilderBidV1 {
  @JsonProperty("header")
  private final ExecutionPayloadHeaderV1 header;

  @JsonSerialize(using = UInt256AsHexSerializer.class)
  @JsonDeserialize(using = UInt256AsHexDeserializer.class)
  private final UInt256 value;

  @JsonSerialize(using = BLSPubKeySerializer.class)
  @JsonDeserialize(using = BLSPubKeyDeserializer.class)
  private final BLSPubKey pubkey;

  @JsonCreator
  public BuilderBidV1(
      @JsonProperty("header") ExecutionPayloadHeaderV1 header,
      @JsonProperty("value") UInt256 value,
      @JsonProperty("pubkey") BLSPubKey pubkey) {
    checkNotNull(header, "header cannot be null");
    checkNotNull(value, "value cannot be null");
    checkNotNull(pubkey, "pubkey cannot be null");

    this.header = header;
    this.value = value;
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
    final BuilderBidV1 that = (BuilderBidV1) o;
    return Objects.equals(header, that.header)
        && Objects.equals(value, that.value)
        && Objects.equals(pubkey, that.pubkey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(header, value, pubkey);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("header", header)
        .add("value", value)
        .add("pubkey", pubkey)
        .toString();
  }
}
