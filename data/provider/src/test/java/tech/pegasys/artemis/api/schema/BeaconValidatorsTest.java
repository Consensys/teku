/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.api.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.api.schema.BeaconValidators.PAGE_SIZE_DEFAULT;
import static tech.pegasys.artemis.api.schema.BeaconValidators.PAGE_TOKEN_DEFAULT;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.MutableValidator;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;

class BeaconValidatorsTest {

  @Test
  public void validatorsResponseShouldConformToDefaults() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(99);
    SSZList<Validator> validatorList = beaconState.getValidators();
    BeaconValidators response = new BeaconValidators(validatorList);
    assertThat(response.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(response.validatorList.size())
        .isEqualTo(Math.min(validatorList.size(), PAGE_SIZE_DEFAULT));
    int expectedNextPageToken =
        validatorList.size() < PAGE_SIZE_DEFAULT ? 0 : PAGE_TOKEN_DEFAULT + 1;
    assertThat(response.getNextPageToken()).isEqualTo(expectedNextPageToken);
    assertThat(response.validatorList.get(0).validator.activation_eligibility_epoch)
        .isEqualToComparingFieldByField(validatorList.get(0).getActivation_eligibility_epoch());
    assertThat(response.validatorList.get(0).index).isEqualTo(0);
  }

  @Test
  public void activeValidatorsResponseShouldConformToDefaults() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(98);
    BeaconValidators validators =
        new BeaconValidators(
            beaconState.getValidators(),
            true,
            BeaconStateUtil.get_current_epoch(beaconState),
            PAGE_SIZE_DEFAULT,
            PAGE_TOKEN_DEFAULT);
    int expectedNextPageToken =
        beaconState.getValidators().size() < PAGE_SIZE_DEFAULT ? 0 : PAGE_TOKEN_DEFAULT + 1;
    long activeValidatorCount =
        BeaconValidators.getEffectiveListSize(
            getValidators(beaconState),
            true,
            BeaconStateUtil.compute_epoch_at_slot(beaconState.getSlot()));
    assertThat(validators.validatorList.size())
        .isEqualTo(Math.min(PAGE_SIZE_DEFAULT, activeValidatorCount));
    assertThat(validators.getTotalSize()).isEqualTo(activeValidatorCount);
    assertThat(validators.getNextPageToken()).isEqualTo(expectedNextPageToken);
  }

  @Test
  public void suppliedPageSizeParamIsUsed() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final int suppliedPageSizeParam = 10;

    BeaconValidators beaconValidators =
        new BeaconValidators(
            beaconState.getValidators(),
            false,
            Constants.FAR_FUTURE_EPOCH,
            suppliedPageSizeParam,
            PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.validatorList.size()).isEqualTo(suppliedPageSizeParam);
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT + 1);
  }

  @Test
  public void suppliedPageParamsAreUsed() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final int suppliedPageSizeParam = 9;
    final int suppliedPageTokenParam = 2;

    BeaconValidators beaconValidators =
        new BeaconValidators(
            beaconState.getValidators(),
            false,
            Constants.FAR_FUTURE_EPOCH,
            suppliedPageSizeParam,
            suppliedPageTokenParam);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(suppliedPageTokenParam + 1);
    assertThat(beaconValidators.validatorList.size()).isEqualTo(suppliedPageSizeParam);
  }

  @Test
  public void returnEmptyListIfPageParamsOutOfBounds() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final int suppliedPageSizeParam = 1000;
    final int suppliedPageTokenParam = 1000;

    BeaconValidators beaconValidators =
        new BeaconValidators(
            beaconState.getValidators(),
            false,
            Constants.FAR_FUTURE_EPOCH,
            suppliedPageSizeParam,
            suppliedPageTokenParam);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(suppliedPageSizeParam * suppliedPageTokenParam)
        .isGreaterThan(beaconValidators.validatorList.size());
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.validatorList.size()).isEqualTo(0);
  }

  @Test
  public void returnRemainderIfEdgeCasePageParams() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final SSZList<Validator> validators = beaconState.getValidators();
    final int validatorsSize = validators.size();
    final int suppliedPageSizeParam = validatorsSize / 10 - 1;
    final int suppliedPageTokenParam = 11;
    final int expectedRemainderSize =
        validatorsSize - (suppliedPageSizeParam * suppliedPageTokenParam);
    assertThat(expectedRemainderSize).isLessThan(PAGE_SIZE_DEFAULT);
    assertThat(expectedRemainderSize).isGreaterThan(0);

    BeaconValidators beaconValidators =
        new BeaconValidators(
            validators,
            false,
            Constants.FAR_FUTURE_EPOCH,
            suppliedPageSizeParam,
            suppliedPageTokenParam);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.validatorList.size()).isEqualTo(expectedRemainderSize);
  }

  @Test
  public void getActiveValidatorsCount() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(23);
    MutableBeaconState beaconStateW = beaconState.createWritableCopy();

    SSZList<Validator> allValidators = beaconState.getValidators();
    long originalActiveValidatorCount =
        BeaconValidators.getEffectiveListSize(
            getValidators(beaconState),
            true,
            BeaconStateUtil.compute_epoch_at_slot(beaconStateW.getSlot()));
    int originalValidatorCount = allValidators.size();

    assertThat(originalActiveValidatorCount)
        .isLessThanOrEqualTo(beaconStateW.getValidators().size());

    // create one validator which IS active and add it to the list
    MutableValidator v = DataStructureUtil.randomValidator(77).createWritableCopy();
    v.setActivation_eligibility_epoch(UnsignedLong.ZERO);
    v.setActivation_epoch(UnsignedLong.valueOf(Constants.GENESIS_EPOCH));
    beaconStateW.getValidators().add(v);
    beaconStateW.commitChanges();

    int updatedValidatorCount = beaconStateW.getValidators().size();
    long updatedActiveValidatorCount =
        BeaconValidators.getEffectiveListSize(
            getValidators(beaconStateW),
            true,
            BeaconStateUtil.compute_epoch_at_slot(beaconStateW.getSlot()));

    SSZList<Validator> updatedValidators = beaconStateW.getValidators();

    assertThat(updatedValidators).contains(v);
    assertThat(beaconStateW.getValidators()).contains(v);
    assertThat(updatedValidatorCount).isEqualTo(originalValidatorCount + 1);
    assertThat(updatedActiveValidatorCount).isLessThanOrEqualTo(updatedValidatorCount);
    // same number of non-active validators before and after
    assertThat(updatedValidatorCount - updatedActiveValidatorCount)
        .isEqualTo(originalValidatorCount - originalActiveValidatorCount);
  }

  private List<tech.pegasys.artemis.api.schema.Validator> getValidators(BeaconState beaconState) {
    tech.pegasys.artemis.api.schema.BeaconState state =
        new tech.pegasys.artemis.api.schema.BeaconState(beaconState);
    return state.validators;
  }
}
