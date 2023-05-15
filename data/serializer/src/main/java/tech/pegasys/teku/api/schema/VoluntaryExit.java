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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("JavaCase")
public class VoluntaryExit {
  @Schema(type = "string", format = "uint64")
  public UInt64 epoch;

  @Schema(type = "string", format = "uint64")
  public UInt64 validator_index;

  public static final DeserializableTypeDefinition<VoluntaryExit> VOLUNTARY_EXIT_TYPE =
      DeserializableTypeDefinition.object(VoluntaryExit.class)
          .initializer(VoluntaryExit::new)
          .withField(
              "epoch", CoreTypes.UINT64_TYPE, VoluntaryExit::getEpoch, VoluntaryExit::setEpoch)
          .withField(
              "validator_index",
              CoreTypes.UINT64_TYPE,
              VoluntaryExit::getValidatorIndex,
              VoluntaryExit::setValidatorIndex)
          .build();

  public VoluntaryExit() {}

  public VoluntaryExit(
      tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit voluntaryExit) {
    this.epoch = voluntaryExit.getEpoch();
    this.validator_index = voluntaryExit.getValidatorIndex();
  }

  @JsonCreator
  public VoluntaryExit(
      @JsonProperty("epoch") final UInt64 epoch,
      @JsonProperty("validator_index") final UInt64 validator_index) {
    this.epoch = epoch;
    this.validator_index = validator_index;
  }

  public UInt64 getEpoch() {
    return epoch;
  }

  public void setEpoch(UInt64 epoch) {
    this.epoch = epoch;
  }

  public UInt64 getValidatorIndex() {
    return validator_index;
  }

  public void setValidatorIndex(UInt64 validator_index) {
    this.validator_index = validator_index;
  }

  public tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit asInternalVoluntaryExit() {
    return new tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit(
        epoch, validator_index);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VoluntaryExit)) {
      return false;
    }
    VoluntaryExit that = (VoluntaryExit) o;
    return Objects.equals(epoch, that.epoch)
        && Objects.equals(validator_index, that.validator_index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, validator_index);
  }
}
