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

package tech.pegasys.artemis.datastructures.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_initial_beacon_state;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposits;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateWithCacheTest {

  private BeaconState newState(int numDeposits) {

    try {

      // Initialize state
      BeaconStateWithCache state = new BeaconStateWithCache();
      get_initial_beacon_state(
          state,
          randomDeposits(numDeposits),
          UnsignedLong.ZERO,
          new Eth1Data(Bytes32.ZERO, Bytes32.ZERO));

      return state;
    } catch (Exception e) {
      fail("get_initial_beacon_state() failed");
      return null;
    }
  }

  @Test
  void deepCopyModifyForkDoesNotEqualTest() {
    BeaconStateWithCache state = (BeaconStateWithCache) newState(1);
    BeaconState deepCopy = BeaconStateWithCache.deepCopy(state);

    // Test slot
    state.incrementSlot();
    assertThat(deepCopy.getSlot()).isNotEqualTo(state.getSlot());

    // Test fork
    state.setFork(new Fork(UnsignedLong.valueOf(1), UnsignedLong.ONE, UnsignedLong.ONE));
    assertThat(deepCopy.getFork().getPrevious_version())
        .isNotEqualTo(state.getFork().getPrevious_version());
  }

  @Test
  void deepCopyModifyIncrementSlotDoesNotEqualTest() {
    BeaconStateWithCache state = (BeaconStateWithCache) newState(1);
    BeaconState deepCopy = BeaconStateWithCache.deepCopy(state);

    // Test slot
    state.incrementSlot();
    assertThat(deepCopy.getSlot()).isNotEqualTo(state.getSlot());
  }

  @Test
  void deepCopyModifyModifyValidatorsDoesNotEqualTest() {
    BeaconStateWithCache state = (BeaconStateWithCache) newState(1);

    // Test validator registry
    ArrayList<Validator> new_records =
        new ArrayList<>(
            Collections.nCopies(
                12,
                new Validator(
                    BLSPublicKey.empty(),
                    Bytes32.ZERO,
                    UnsignedLong.ZERO,
                    UnsignedLong.valueOf(GENESIS_EPOCH),
                    UnsignedLong.ZERO,
                    false,
                    false)));
    state.setValidator_registry(new_records);
    BeaconState deepCopy = BeaconStateWithCache.deepCopy(state);
    Validator validator = deepCopy.getValidator_registry().get(0);
    validator.setPubkey(BLSPublicKey.random(9999999));
    assertThat(deepCopy.getValidator_registry().get(0).getPubkey())
        .isNotEqualTo(state.getValidator_registry().get(0).getPubkey());
  }
}
