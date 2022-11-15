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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WithdrawalV1 {
  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 index;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 validatorIndex;

  @JsonSerialize(using = Bytes20Serializer.class)
  @JsonDeserialize(using = Bytes20Deserializer.class)
  public final Bytes20 address;

  @JsonSerialize(using = UInt256AsHexSerializer.class)
  @JsonDeserialize(using = UInt256AsHexDeserializer.class)
  public final UInt256 amount;

  public WithdrawalV1(
      @JsonProperty("index") final UInt64 index,
      @JsonProperty("validatorIndex") final UInt64 validatorIndex,
      @JsonProperty("address") final Bytes20 address,
      @JsonProperty("amount") final UInt256 amount) {
    this.index = index;
    this.validatorIndex = validatorIndex;
    this.address = address;
    this.amount = amount;
  }
}
