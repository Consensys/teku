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

package tech.pegasys.teku.api.request.v1.validator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorLivenessRequest {

  @ArraySchema(schema = @Schema(type = "string", format = "uint64"))
  public final List<UInt64> indices;

  @JsonCreator
  public ValidatorLivenessRequest(@JsonProperty("indices") final List<UInt64> indices) {
    this.indices = indices;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorLivenessRequest that = (ValidatorLivenessRequest) o;
    return Objects.equals(indices, that.indices);
  }

  @Override
  public int hashCode() {
    return Objects.hash(indices);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("indices", indices).toString();
  }
}
