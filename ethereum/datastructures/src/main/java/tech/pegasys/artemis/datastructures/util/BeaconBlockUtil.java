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

package tech.pegasys.artemis.datastructures.util;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class BeaconBlockUtil {
  /**
   * Return the block header corresponding to a block with ``state_root`` set to ``ZERO_HASH``.
   *
   * @param block
   * @return
   */
  public static BeaconBlockHeader get_temporary_block_header(BeaconBlock block) {
    return new BeaconBlockHeader(
        UnsignedLong.valueOf(block.getSlot()),
        block.getPrevious_block_root(),
        Constants.ZERO_HASH,
        block.getBody().hash_tree_root(),
        block.getSignature());
  }

  /**
   * Get an empty ``BeaconBlock``.
   *
   * @return
   */
  public static BeaconBlock get_empty_block() {
    return new BeaconBlock(
        Constants.GENESIS_SLOT,
        Constants.ZERO_HASH,
        Constants.ZERO_HASH,
        new BeaconBlockBody(
            Constants.EMPTY_SIGNATURE,
            new Eth1Data(Constants.ZERO_HASH, Constants.ZERO_HASH),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>()),
        Constants.EMPTY_SIGNATURE);
  }
}
