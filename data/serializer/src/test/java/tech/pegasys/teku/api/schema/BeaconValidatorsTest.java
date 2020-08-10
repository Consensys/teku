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

package tech.pegasys.teku.api.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.api.schema.BeaconValidators.PAGE_SIZE_DEFAULT;
import static tech.pegasys.teku.api.schema.BeaconValidators.PAGE_TOKEN_DEFAULT;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.util.config.Constants;

class BeaconValidatorsTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void validatorsResponseShouldConformToDefaults() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
    SSZList<Validator> validatorList = beaconState.getValidators();
    BeaconValidators response = new BeaconValidators(beaconState);
    assertThat(response.total_size).isEqualTo(beaconState.getValidators().size());
    assertThat(response.validators.size())
        .isEqualTo(Math.min(validatorList.size(), PAGE_SIZE_DEFAULT));
    int expectedNextPageToken =
        validatorList.size() < PAGE_SIZE_DEFAULT ? 0 : PAGE_TOKEN_DEFAULT + 1;
    assertThat(response.next_page_token).isEqualTo(expectedNextPageToken);
    assertThat(response.validators.get(0).validator.activation_eligibility_epoch)
        .isEqualToComparingFieldByField(validatorList.get(0).getActivation_eligibility_epoch());
    assertThat(response.validators.get(0).validator_index).isEqualTo(0);
  }

  @Test
  public void activeValidatorsResponseShouldConformToDefaults() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
    BeaconValidators validators =
        new BeaconValidators(
            beaconState,
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
    assertThat(validators.validators.size())
        .isEqualTo(Math.min(PAGE_SIZE_DEFAULT, activeValidatorCount));
    assertThat(validators.total_size).isEqualTo(activeValidatorCount);
    assertThat(validators.next_page_token).isEqualTo(expectedNextPageToken);
  }

  @Test
  public void suppliedPageSizeParamIsUsed() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
    final int suppliedPageSizeParam = 10;

    BeaconValidators beaconValidators =
        new BeaconValidators(
            beaconState,
            false,
            Constants.FAR_FUTURE_EPOCH,
            suppliedPageSizeParam,
            PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.total_size).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.validators.size()).isEqualTo(suppliedPageSizeParam);
    assertThat(beaconValidators.next_page_token).isEqualTo(PAGE_TOKEN_DEFAULT + 1);
  }

  @Test
  public void suppliedPageParamsAreUsed() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
    final int suppliedPageSizeParam = 9;
    final int suppliedPageTokenParam = 2;

    BeaconValidators beaconValidators =
        new BeaconValidators(
            beaconState,
            false,
            Constants.FAR_FUTURE_EPOCH,
            suppliedPageSizeParam,
            suppliedPageTokenParam);
    assertThat(beaconValidators.total_size).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.next_page_token).isEqualTo(suppliedPageTokenParam + 1);
    assertThat(beaconValidators.validators.size()).isEqualTo(suppliedPageSizeParam);
  }

  @Test
  public void returnEmptyListIfPageParamsOutOfBounds() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
    final int suppliedPageSizeParam = 1000;
    final int suppliedPageTokenParam = 1000;

    BeaconValidators beaconValidators =
        new BeaconValidators(
            beaconState,
            false,
            Constants.FAR_FUTURE_EPOCH,
            suppliedPageSizeParam,
            suppliedPageTokenParam);
    assertThat(beaconValidators.total_size).isEqualTo(beaconState.getValidators().size());
    assertThat(suppliedPageSizeParam * suppliedPageTokenParam)
        .isGreaterThan(beaconValidators.validators.size());
    assertThat(beaconValidators.next_page_token).isEqualTo(PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.validators.size()).isEqualTo(0);
  }

  @Test
  public void returnRemainderIfEdgeCasePageParams() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();
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
            beaconState,
            false,
            Constants.FAR_FUTURE_EPOCH,
            suppliedPageSizeParam,
            suppliedPageTokenParam);
    assertThat(beaconValidators.total_size).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.next_page_token).isEqualTo(PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.validators.size()).isEqualTo(expectedRemainderSize);
  }

  @Test
  public void getActiveValidatorsCount() {
    BeaconState beaconState = dataStructureUtil.randomBeaconState();

    System.out.println(beaconState.hashTreeRoot());

    SSZList<Validator> allValidators = beaconState.getValidators();
    long originalActiveValidatorCount =
        BeaconValidators.getEffectiveListSize(
            getValidators(beaconState),
            true,
            BeaconStateUtil.compute_epoch_at_slot(beaconState.getSlot()));
    int originalValidatorCount = allValidators.size();

    assertThat(originalActiveValidatorCount)
        .isLessThanOrEqualTo(beaconState.getValidators().size());

    // create one validator which IS active and add it to the list
    Validator v =
        dataStructureUtil
            .randomValidator()
            .withActivation_eligibility_epoch(UInt64.ZERO)
            .withActivation_epoch(UInt64.valueOf(Constants.GENESIS_EPOCH));

    BeaconState beaconStateW = beaconState.updated(state -> state.getValidators().add(v));

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

  private List<tech.pegasys.teku.api.schema.Validator> getValidators(BeaconState beaconState) {
    tech.pegasys.teku.api.schema.BeaconState state =
        new tech.pegasys.teku.api.schema.BeaconState(beaconState);
    return state.validators;
  }
}
