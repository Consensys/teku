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

package tech.pegasys.artemis.beaconrestapi.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;

public class BeaconBlockResponse {
  private final BeaconBlock beaconBlock;
  private final Bytes32 rootHash;

  public BeaconBlockResponse(final BeaconBlock beaconBlock) {
    this.beaconBlock = beaconBlock;
    this.rootHash = beaconBlock.hash_tree_root();
  }

  @JsonProperty("beacon_block")
  public BeaconBlock getBeaconBlock() {
    return beaconBlock;
  }

  @JsonProperty("root")
  public Bytes32 getRootHash() {
    return rootHash;
  }
}
