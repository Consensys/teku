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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes32;

public class Eth1Data {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 deposit_root;

  @Schema(type = "string", format = "uint64")
  public final UnsignedLong deposit_count;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 block_hash;

  public Eth1Data(final tech.pegasys.teku.datastructures.blocks.Eth1Data eth1Data) {
    deposit_count = eth1Data.getDeposit_count();
    deposit_root = eth1Data.getDeposit_root();
    block_hash = eth1Data.getBlock_hash();
  }

  @JsonCreator
  public Eth1Data(
      @JsonProperty("deposit_root") final Bytes32 deposit_root,
      @JsonProperty("deposit_count") final UnsignedLong deposit_count,
      @JsonProperty("block_hash") final Bytes32 block_hash) {
    this.deposit_root = deposit_root;
    this.deposit_count = deposit_count;
    this.block_hash = block_hash;
  }
}
