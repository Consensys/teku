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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import tech.pegasys.teku.ssz.backing.collections.SszBitvector;

public class ValidatorFlag {
  public static final int TIMELY_HEAD_FLAG = 1;
  public static final int TIMELY_SOURCE_FLAG = 2;
  public static final int TIMELY_TARGET_FLAG = 4;

  public static boolean isTimelyTarget(SszBitvector flags) {
    return flags.getBit(TIMELY_TARGET_FLAG);
  }

  public static boolean isAnyFlagSet(SszBitvector flags) {
    return flags.getBit(TIMELY_HEAD_FLAG)
        || flags.getBit(TIMELY_SOURCE_FLAG)
        || flags.getBit(TIMELY_TARGET_FLAG);
  }
}
