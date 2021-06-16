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

package tech.pegasys.teku.api.response.v1.beacon;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StateSyncCommittees {
  @JsonProperty("validators")
  @ArraySchema(schema = @Schema(type = "string", example = EXAMPLE_UINT64))
  public final List<UInt64> validators;

  // so that swagger can list as [[String]], the getter will be what reflection picks up
  // and the actual variable storing as uint64 will be ignored
  @JsonProperty("validator_aggregates")
  public List<List<String>> getValidatorAggregates() {
    return validatorAggregates.stream()
        .map(e -> e.stream().map(UInt64::toString).collect(Collectors.toList()))
        .collect(Collectors.toList());
  }

  @JsonIgnore public final List<List<UInt64>> validatorAggregates;

  @JsonCreator
  public StateSyncCommittees(
      @JsonProperty("validators") final List<UInt64> validators,
      @JsonProperty("validator_aggregates") final List<List<UInt64>> validatorAggregates) {
    this.validators = validators;
    this.validatorAggregates = validatorAggregates;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final StateSyncCommittees that = (StateSyncCommittees) o;
    return Objects.equals(validators, that.validators)
        && Objects.equals(validatorAggregates, that.validatorAggregates);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validators, validatorAggregates);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("validators", validators)
        .add("validatorAggregates", validatorAggregates)
        .toString();
  }
}
