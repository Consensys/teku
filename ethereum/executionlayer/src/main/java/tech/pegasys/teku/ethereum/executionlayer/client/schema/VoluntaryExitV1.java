/*
 * Copyright 2020 ConsenSys AG.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionlayer.client.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class VoluntaryExitV1 {
  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 epoch;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 validatorIndex;

  public VoluntaryExitV1(
      tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit voluntaryExit) {
    this.epoch = voluntaryExit.getEpoch();
    this.validatorIndex = voluntaryExit.getValidatorIndex();
  }

  @JsonCreator
  public VoluntaryExitV1(
      @JsonProperty("epoch") final UInt64 epoch,
      @JsonProperty("validatorIndex") final UInt64 validatorIndex) {
    this.epoch = epoch;
    this.validatorIndex = validatorIndex;
  }

  public tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit asInternalVoluntaryExit() {
    return new tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit(
        epoch, validatorIndex);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VoluntaryExitV1)) {
      return false;
    }
    VoluntaryExitV1 that = (VoluntaryExitV1) o;
    return Objects.equals(epoch, that.epoch) && Objects.equals(validatorIndex, that.validatorIndex);
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, validatorIndex);
  }
}
