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

package tech.pegasys.artemis.state.util;

import static java.lang.Math.toIntExact;

import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.state.BeaconState;

import java.util.ArrayList;

public class BeaconStateUtil {
    public static Hash get_previous_block_root(BeaconState state){
        ArrayList<Hash> latest_block_roots = state.getLatest_block_roots();
        return latest_block_roots.get(toIntExact(state.getSlot() % Constants.LATEST_BLOCK_ROOTS_LENGTH));
    }
}
