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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.services.executionengine.client.serialization.Bytes32Deserializer;
import tech.pegasys.teku.services.executionengine.client.serialization.BytesSerializer;

public class ForkChoiceStateV1 {
  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 headBlockHash;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 safeBlockHash;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 finalizedBlockHash;

  public ForkChoiceStateV1(
      @JsonProperty("headBlockHash") Bytes32 headBlockHash,
      @JsonProperty("safeBlockHash") Bytes32 safeBlockHash,
      @JsonProperty("finalizedBlockHash") Bytes32 finalizedBlockHash) {
    this.headBlockHash = headBlockHash;
    this.safeBlockHash = safeBlockHash;
    this.finalizedBlockHash = finalizedBlockHash;
  }
}
