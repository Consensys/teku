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

package tech.pegasys.teku.api.response;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES4;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class GetForkResponse {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES4)
  public Bytes4 previous_version;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES4)
  public Bytes4 current_version;

  @Schema(type = "string", format = "uint64")
  public UInt64 epoch;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 genesis_validators_root;

  @JsonCreator
  public GetForkResponse(
      @JsonProperty("previous_version") final Bytes4 previous_version,
      @JsonProperty("current_version") final Bytes4 current_version,
      @JsonProperty("epoch") final UInt64 epoch,
      @JsonProperty("genesis_validators_root") final Bytes32 genesis_validators_root) {
    this.previous_version = previous_version;
    this.current_version = current_version;
    this.epoch = epoch;
    this.genesis_validators_root = genesis_validators_root;
  }

  public GetForkResponse(final ForkInfo forkInfo) {
    final Fork fork = forkInfo.getFork();
    if (fork != null) {
      this.previous_version = fork.getPrevious_version();
      this.current_version = fork.getCurrent_version();
      this.epoch = fork.getEpoch();
    }
    genesis_validators_root = forkInfo.getGenesisValidatorsRoot();
  }

  public tech.pegasys.teku.datastructures.state.ForkInfo asInternalForkInfo() {
    return new tech.pegasys.teku.datastructures.state.ForkInfo(
        new Fork(previous_version, current_version, epoch), genesis_validators_root);
  }
}
