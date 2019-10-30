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

package tech.pegasys.artemis.provider;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.provider.JsonProvider.printBeaconState;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;

class JsonProviderTest {

  @Test
  void printBeaconStateTest() {
    Constants.setConstants("minimal");
    BeaconState state = DataStructureUtil.randomBeaconState(UnsignedLong.valueOf(16), 100);
    String jsonState = printBeaconState(state);
    System.out.println(jsonState);
    assertTrue(jsonState.length() > 0);
  }
}
