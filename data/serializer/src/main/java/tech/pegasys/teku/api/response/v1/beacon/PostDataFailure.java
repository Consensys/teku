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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PostDataFailure {
  @Schema(type = "string", format = "uint64")
  public final UInt64 index;

  public final String message;

  @JsonCreator
  public PostDataFailure(
      @JsonProperty("index") final UInt64 index, @JsonProperty("message") final String message) {
    this.index = index;
    this.message = message;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PostDataFailure that = (PostDataFailure) o;
    return Objects.equals(index, that.index) && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, message);
  }
}
