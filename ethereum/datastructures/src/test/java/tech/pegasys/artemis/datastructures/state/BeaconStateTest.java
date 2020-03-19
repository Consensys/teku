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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateTest {

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
    MutableBeaconState stateW1 = BeaconState.createEmpty().createWritableCopy();
    UnsignedLong val1 = UnsignedLong.valueOf(0x3333);
    stateW1.getBalances().add(val1);
    UnsignedLong v2 = stateW1.getBalances().get(0);
    UnsignedLong v3 = stateW1.getBalances().get(0);
    BeaconState stateR1 = stateW1.commitChanges();
    UnsignedLong v4 = stateR1.getBalances().get(0);

    assertThat(stateR1.getBalances().size()).isEqualTo(1);
    assertThat(stateR1.getBalances().get(0)).isEqualTo(UnsignedLong.valueOf(0x3333));

    MutableBeaconState stateW2 = stateR1.createWritableCopy();
    UnsignedLong v5 = stateW2.getBalances().get(0);
    stateW2.getBalances().add(UnsignedLong.valueOf(0x4444));
    UnsignedLong v6 = stateW2.getBalances().get(0);
    BeaconState stateR2 = stateW2.commitChanges();
    UnsignedLong v7 = stateR2.getBalances().get(0);

    // check that view caching is effectively works and the value
    // is not recreated from tree node without need
    assertThat(v2).isSameAs(val1);
    assertThat(v3).isSameAs(val1);
    assertThat(v4).isSameAs(val1);
    assertThat(v5).isSameAs(val1);
    assertThat(v6).isSameAs(val1);
    assertThat(v7).isSameAs(val1);
  }
}
