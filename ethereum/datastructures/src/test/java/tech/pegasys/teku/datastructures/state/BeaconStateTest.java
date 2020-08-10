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

package tech.pegasys.teku.datastructures.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths =
        List.of(
            Constants.SLOTS_PER_HISTORICAL_ROOT,
            Constants.SLOTS_PER_HISTORICAL_ROOT,
            Constants.EPOCHS_PER_HISTORICAL_VECTOR,
            Constants.EPOCHS_PER_SLASHINGS_VECTOR);
    assertEquals(
        vectorLengths,
        SimpleOffsetSerializer.classReflectionInfo.get(BeaconStateImpl.class).getVectorLengths());
  }

  @Test
  void simpleMutableBeaconStateTest() {
    UInt64 val1 = UInt64.valueOf(0x3333);
    BeaconState stateR1 =
        BeaconState.createEmpty()
            .updated(
                state -> {
                  state.getBalances().add(val1);
                });
    UInt64 v1 = stateR1.getBalances().get(0);

    assertThat(stateR1.getBalances().size()).isEqualTo(1);
    assertThat(stateR1.getBalances().get(0)).isEqualTo(UInt64.valueOf(0x3333));

    BeaconState stateR2 =
        stateR1.updated(
            state -> {
              state.getBalances().add(UInt64.valueOf(0x4444));
            });
    UInt64 v2 = stateR2.getBalances().get(0);

    // check that view caching is effectively works and the value
    // is not recreated from tree node without need
    assertThat(v1).isSameAs(val1);
    assertThat(v2).isSameAs(val1);
  }

  @Test
  public void changeSpecConstantsTest() {
    try {
      BeaconState s1 = BeaconState.createEmpty();

      Constants.SLOTS_PER_HISTORICAL_ROOT = 123;
      Constants.HISTORICAL_ROOTS_LIMIT = 123;
      Constants.EPOCHS_PER_ETH1_VOTING_PERIOD = 123;
      Constants.VALIDATOR_REGISTRY_LIMIT = 123;
      Constants.EPOCHS_PER_HISTORICAL_VECTOR = 123;
      Constants.EPOCHS_PER_SLASHINGS_VECTOR = 123;
      Constants.MAX_ATTESTATIONS = 123;

      // this call should reset all the memorized spec constants
      BeaconState s2 = BeaconState.createEmpty();

      assertThat(s1.getBlock_roots().getMaxSize()).isNotEqualTo(s2.getBlock_roots().getMaxSize());
      assertThat(s1.getState_roots().getMaxSize()).isNotEqualTo(s2.getState_roots().getMaxSize());
      assertThat(s1.getHistorical_roots().getMaxSize())
          .isNotEqualTo(s2.getHistorical_roots().getMaxSize());
      assertThat(s1.getEth1_data_votes().getMaxSize())
          .isNotEqualTo(s2.getEth1_data_votes().getMaxSize());
      assertThat(s1.getValidators().getMaxSize()).isNotEqualTo(s2.getValidators().getMaxSize());
      assertThat(s1.getBalances().getMaxSize()).isNotEqualTo(s2.getBalances().getMaxSize());
      assertThat(s1.getRandao_mixes().getMaxSize()).isNotEqualTo(s2.getRandao_mixes().getMaxSize());
      assertThat(s1.getSlashings().getMaxSize()).isNotEqualTo(s2.getSlashings().getMaxSize());
      assertThat(s1.getPrevious_epoch_attestations().getMaxSize())
          .isNotEqualTo(s2.getPrevious_epoch_attestations().getMaxSize());
      assertThat(s1.getCurrent_epoch_attestations().getMaxSize())
          .isNotEqualTo(s2.getCurrent_epoch_attestations().getMaxSize());
    } finally {
      Constants.setConstants("minimal");
    }
  }

  @Test
  void roundTripViaSsz() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
    Bytes bytes = SimpleOffsetSerializer.serialize(beaconState);
    BeaconState state = SimpleOffsetSerializer.deserialize(bytes, BeaconStateImpl.class);
    assertEquals(beaconState, state);
  }
}
