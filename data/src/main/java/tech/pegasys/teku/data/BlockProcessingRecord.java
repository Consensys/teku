/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.data;

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;

public class BlockProcessingRecord {
  private final SignedBeaconBlock block;
  private final BeaconState postState;

  public BlockProcessingRecord(final SignedBeaconBlock block, final BeaconState postState) {
    this.block = block;
    this.postState = postState;
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public BeaconState getPostState() {
    return postState;
  }
}
