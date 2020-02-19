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

package tech.pegasys.artemis.validator.coordinator;

import static tech.pegasys.artemis.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;

public class ValidatorCoordinatorUtil {

  static boolean isEpochStart(UnsignedLong slot) {
    return slot.mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).equals(UnsignedLong.ZERO);
  }

  static boolean isGenesis(UnsignedLong slot) {
    return slot.equals(UnsignedLong.valueOf(GENESIS_SLOT));
  }
}
