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
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateTest {

  @Test
  public void getActiveValidators() {

    BeaconState beaconState = DataStructureUtil.randomBeaconState(23);
    SSZList<Validator> allValidators = beaconState.getValidators();
    List<Validator> activeValidators = beaconState.getActiveValidators();
    int originalValidatorCount = allValidators.size();

    assertThat(activeValidators.size()).isLessThanOrEqualTo(allValidators.size());

    // create one validator which IS active and add it to the list
    Validator v = DataStructureUtil.randomValidator(77);
    v.setActivation_eligibility_epoch(UnsignedLong.ZERO);
    v.setActivation_epoch(beaconState.getFinalized_checkpoint().getEpoch().minus(UnsignedLong.ONE));
    allValidators.add(v);
    beaconState.setValidators(allValidators);
    int updatedValidatorCount = allValidators.size();
    List<Validator> updatedActiveValidators = beaconState.getActiveValidators();

    assertThat(updatedActiveValidators).contains(v);
    assertThat(beaconState.getValidators()).contains(v);
    assertThat(beaconState.getValidators()).containsAll(updatedActiveValidators);
    assertThat(updatedValidatorCount).isEqualTo(originalValidatorCount + 1);
    assertThat(updatedActiveValidators.size()).isLessThanOrEqualTo(updatedValidatorCount);
    // same number of non-active validators before and after
    assertThat(updatedValidatorCount - updatedActiveValidators.size())
        .isEqualTo(originalValidatorCount - activeValidators.size());
  }

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
        SimpleOffsetSerializer.classReflectionInfo.get(BeaconState.class).getVectorLengths());
  }
}
