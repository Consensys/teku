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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES48;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class ValidatorsRequest {
  @Schema(type = "string", format = "uint64")
  public final UInt64 epoch;

  @ArraySchema(
      schema = @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48))
  public final List<BLSPubKey> pubkeys;

  @JsonCreator
  public ValidatorsRequest(
      @JsonProperty(value = "epoch") UInt64 epoch,
      @JsonProperty(value = "pubkeys", required = true) final List<BLSPubKey> pubkeys) {
    this.epoch = epoch;
    this.pubkeys = pubkeys;
    // Restrict valid epoch values to ones that can be converted to slot without overflowing
    if (epoch != null
        && epoch.isGreaterThan(UInt64.MAX_VALUE.dividedBy(Constants.SLOTS_PER_EPOCH))) {
      throw new IllegalArgumentException("Epoch is too large.");
    }
  }
}
