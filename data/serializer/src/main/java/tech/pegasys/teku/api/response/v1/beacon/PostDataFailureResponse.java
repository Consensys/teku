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
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PostDataFailureResponse {
  @Schema(type = "string", format = "uint64")
  public final UInt64 code;

  public final String message;
  public final List<PostDataFailure> failures;

  @JsonCreator
  public PostDataFailureResponse(
      @JsonProperty("code") final int code,
      @JsonProperty("message") final String message,
      @JsonProperty("failures") final List<PostDataFailure> failures) {
    this.code = UInt64.valueOf(code);
    this.message = message;
    this.failures = failures;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PostDataFailureResponse that = (PostDataFailureResponse) o;
    return Objects.equals(code, that.code)
        && Objects.equals(message, that.message)
        && Objects.equals(failures, that.failures);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, failures);
  }
}
