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

package tech.pegasys.teku.data.slashinginterchange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Metadata {

  public static final UInt64 INTERCHANGE_VERSION = UInt64.valueOf(5);

  @JsonProperty("interchange_format")
  public final String interchangeFormat;

  @JsonProperty("interchange_format_version")
  public final UInt64 interchangeFormatVersion;

  @JsonProperty("genesis_validators_root")
  public final Bytes32 genesisValidatorsRoot;

  public Metadata(final UInt64 interchangeFormatVersion, final Bytes32 genesisValidatorsRoot) {
    this.interchangeFormat = null;
    this.interchangeFormatVersion = interchangeFormatVersion;
    this.genesisValidatorsRoot = genesisValidatorsRoot;
  }

  @JsonCreator
  public Metadata(
      @JsonProperty("interchange_format") final String interchangeFormat,
      @JsonProperty("interchange_format_version") final UInt64 interchangeFormatVersion,
      @JsonProperty("genesis_validators_root") final Bytes32 genesisValidatorsRoot) {
    this.interchangeFormatVersion = interchangeFormatVersion;
    this.genesisValidatorsRoot = genesisValidatorsRoot;
    this.interchangeFormat = interchangeFormat;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Metadata metadata = (Metadata) o;
    return Objects.equals(interchangeFormatVersion, metadata.interchangeFormatVersion)
        && Objects.equals(genesisValidatorsRoot, metadata.genesisValidatorsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(interchangeFormatVersion, genesisValidatorsRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("interchangeFormat", interchangeFormat)
        .add("interchangeFormatVersion", interchangeFormatVersion)
        .add("genesisValidatorsRoot", genesisValidatorsRoot)
        .toString();
  }
}
