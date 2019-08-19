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
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.alogger.ALogger;

public class BeaconBlockUtil {
  private static final ALogger LOG = new ALogger(BeaconBlockUtil.class.getName());

  /**
   * Get an empty ``BeaconBlock``.
   *
   * @return
   */
  public static BeaconBlock get_empty_block() {
    return new BeaconBlock(
        UnsignedLong.valueOf(Constants.GENESIS_SLOT),
        Constants.ZERO_HASH,
        Constants.ZERO_HASH,
        new BeaconBlockBody(),
        Constants.EMPTY_SIGNATURE);
  }
}
