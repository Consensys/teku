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

package tech.pegasys.teku.api.response.v1.node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public class Version {
  @JsonProperty("version")
  @Schema(
      description =
          "A string which uniquely identifies the client implementation and its version; "
              + "similar to [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3).",
      example = "teku/v0.12.6-dev-994997f8/osx-x86_64/adoptopenjdk-java-11")
  public final String version;

  @JsonCreator
  public Version(@JsonProperty("version") final String version) {
    this.version = version;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Version version1 = (Version) o;
    return Objects.equals(version, version1.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version);
  }
}
