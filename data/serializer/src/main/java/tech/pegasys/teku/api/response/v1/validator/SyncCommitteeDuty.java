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

package tech.pegasys.teku.api.response.v1.validator;

import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_PUBKEY;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_PUBKEY;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SyncCommitteeDuty {
  @JsonProperty("pubkey")
  @Schema(
      type = "string",
      pattern = PATTERN_PUBKEY,
      example = EXAMPLE_PUBKEY,
      description =
          "The validator's BLS public key, uniquely identifying them. "
              + "48-bytes, hex encoded with 0x prefix, case insensitive.")
  public final BLSPubKey pubkey;

  @JsonProperty("validator_index")
  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "Index of validator in validator registry")
  public final UInt64 validatorIndex;

  @JsonProperty("validator_sync_committee_indices")
  @ArraySchema(
      schema =
          @Schema(type = "string", example = EXAMPLE_UINT64, description = "The committee index"))
  public final Set<UInt64> committeeIndices;

  @JsonCreator
  public SyncCommitteeDuty(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("validator_index") final UInt64 validatorIndex,
      @JsonProperty("validator_sync_committee_indices") final Set<Integer> committeeIndices) {
    this.pubkey = pubkey;
    this.validatorIndex = validatorIndex;
    this.committeeIndices =
        committeeIndices.stream().map(UInt64::valueOf).collect(Collectors.toSet());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SyncCommitteeDuty that = (SyncCommitteeDuty) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(validatorIndex, that.validatorIndex)
        && Objects.equals(committeeIndices, that.committeeIndices);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, validatorIndex, committeeIndices);
  }
}
