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

package tech.pegasys.teku.api.response.v1.validator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorLivenessAtEpoch {
  @Schema(type = "string", format = "uint64")
  public final UInt64 index;

  @JsonProperty("is_live")
  public final boolean isLive;

  @JsonCreator
  public ValidatorLivenessAtEpoch(
      @JsonProperty("index") final UInt64 index, @JsonProperty("is_live") final boolean isLive) {
    this.index = index;
    this.isLive = isLive;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorLivenessAtEpoch that = (ValidatorLivenessAtEpoch) o;
    return isLive == that.isLive && Objects.equals(index, that.index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, isLive);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("index", index).add("isLive", isLive).toString();
  }
}
