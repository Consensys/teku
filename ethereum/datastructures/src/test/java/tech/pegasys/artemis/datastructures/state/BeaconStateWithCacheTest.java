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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initialize_beacon_state_from_eth1;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposits;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateWithCacheTest {

  private BeaconState newState(int numDeposits) {

    try {

      // Initialize state
      BeaconState state = initialize_beacon_state_from_eth1(
              Bytes32.ZERO,
              UnsignedLong.ZERO,
              randomDeposits(numDeposits));

      return state;
    } catch (Exception e) {
      fail("get_genesis_beacon_state() failed");
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
    state.setFork(new Fork(Bytes.random(4), Bytes.random(4), UnsignedLong.ONE));
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
                                    false,
                                    UnsignedLong.ZERO,
                                    UnsignedLong.valueOf(GENESIS_EPOCH),
                                    UnsignedLong.ZERO,
                                    UnsignedLong.ZERO)));
    state.setValidators(new_records);
    BeaconState deepCopy = BeaconStateWithCache.deepCopy(state);
    Validator validator = deepCopy.getValidators().get(0);
    validator.setPubkey(BLSPublicKey.random(9999999));
    assertThat(deepCopy.getValidators().get(0).getPubkey())
        .isNotEqualTo(state.getValidators().get(0).getPubkey());
  }
}
