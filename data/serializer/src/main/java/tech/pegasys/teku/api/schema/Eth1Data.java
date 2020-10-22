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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Eth1Data {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 deposit_root;

  @Schema(type = "string", format = "uint64")
  public final UInt64 deposit_count;

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
      @JsonProperty("deposit_count") final UInt64 deposit_count,
      @JsonProperty("block_hash") final Bytes32 block_hash) {
    this.deposit_root = deposit_root;
    this.deposit_count = deposit_count;
    this.block_hash = block_hash;
  }

  public tech.pegasys.teku.datastructures.blocks.Eth1Data asInternalEth1Data() {
    return new tech.pegasys.teku.datastructures.blocks.Eth1Data(
        deposit_root, deposit_count, block_hash);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Eth1Data)) return false;
    Eth1Data eth1Data = (Eth1Data) o;
    return Objects.equals(deposit_root, eth1Data.deposit_root)
        && Objects.equals(deposit_count, eth1Data.deposit_count)
        && Objects.equals(block_hash, eth1Data.block_hash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deposit_root, deposit_count, block_hash);
  }
}
