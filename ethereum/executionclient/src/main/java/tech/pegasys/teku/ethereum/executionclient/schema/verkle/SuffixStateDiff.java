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

package tech.pegasys.teku.ethereum.executionclient.schema.verkle;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.ByteUInt8Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.ByteUint8Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.spec.datastructures.execution.verkle.SuffixStateDiffSchema;

public class SuffixStateDiff {

  @JsonSerialize(using = ByteUInt8Serializer.class)
  @JsonDeserialize(using = ByteUint8Deserializer.class)
  @JsonProperty("suffix")
  private final Byte suffix;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  @JsonProperty("currentValue")
  private final Bytes32 currentValue;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  @JsonProperty("newValue")
  private final Bytes32 newValue;

  public SuffixStateDiff(
      @JsonProperty("suffix") final Byte suffix,
      @JsonProperty("currentValue") Bytes32 currentValue,
      @JsonProperty("newValue") Bytes32 newValue) {
    this.suffix = suffix;
    this.currentValue = currentValue;
    this.newValue = newValue;
  }

  public SuffixStateDiff(
      final tech.pegasys.teku.spec.datastructures.execution.verkle.SuffixStateDiff
          suffixStateDiff) {
    this.suffix = suffixStateDiff.getSuffix();
    this.currentValue = suffixStateDiff.getCurrentValue().orElse(null);
    this.newValue = suffixStateDiff.getNewValue().orElse(null);
  }

  public tech.pegasys.teku.spec.datastructures.execution.verkle.SuffixStateDiff
      asInternalSuffixStateDiff(final SuffixStateDiffSchema schema) {
    return schema.create(suffix, Optional.ofNullable(currentValue), Optional.ofNullable(newValue));
  }
}
