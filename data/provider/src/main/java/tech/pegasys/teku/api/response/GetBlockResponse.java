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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;

public class GetBlockResponse {

  @JsonProperty("beacon_block")
  public final SignedBeaconBlock signedBeaconBlock;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final String root;

  public GetBlockResponse(
      final tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock signedBeaconBlock) {
    this.signedBeaconBlock = new SignedBeaconBlock(signedBeaconBlock);
    this.root = signedBeaconBlock.getMessage().hash_tree_root().toHexString().toLowerCase();
  }

  @JsonCreator
  public GetBlockResponse(
      @JsonProperty("root") final String root,
      @JsonProperty("beacon_block") final SignedBeaconBlock signedBeaconBlock) {
    this.root = root;
    this.signedBeaconBlock = signedBeaconBlock;
  }
}
