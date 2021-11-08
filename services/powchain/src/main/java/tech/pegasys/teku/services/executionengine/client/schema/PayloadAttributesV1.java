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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.executionengine.client.serialization.Bytes20Deserializer;
import tech.pegasys.teku.services.executionengine.client.serialization.Bytes20Serializer;
import tech.pegasys.teku.services.executionengine.client.serialization.Bytes32Deserializer;
import tech.pegasys.teku.services.executionengine.client.serialization.BytesSerializer;
import tech.pegasys.teku.services.executionengine.client.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.services.executionengine.client.serialization.UInt64AsHexSerializer;
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

  public PayloadAttributesV1(UInt64 timestamp, Bytes32 random, Bytes20 feeRecipient) {
    this.timestamp = timestamp;
    this.random = random;
    this.feeRecipient = feeRecipient;
  }
}
