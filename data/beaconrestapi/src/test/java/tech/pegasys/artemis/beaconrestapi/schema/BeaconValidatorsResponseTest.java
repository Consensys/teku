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

package tech.pegasys.artemis.beaconrestapi.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_SIZE_DEFAULT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_TOKEN_DEFAULT;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;

class BeaconValidatorsResponseTest {

  @Test
  public void validatorsResponseShouldConformToDefaults() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(99);
    List<Validator> validatorList = beaconState.getValidators();
    BeaconValidatorsResponse response = new BeaconValidatorsResponse(validatorList);
    assertThat(response.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(response.validatorList.size())
        .isLessThanOrEqualTo(RestApiConstants.PAGE_SIZE_DEFAULT);
    assertThat(response.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT + 1);
    assertThat(response.validatorList.get(0).validator).isEqualTo(validatorList.get(0));
    assertThat(response.validatorList.get(0).index).isEqualTo(0);
  }

  @Test
  public void activeValidatorsResponseShouldConformToDefaults() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(98);
    BeaconValidatorsResponse validators =
        new BeaconValidatorsResponse(beaconState.getActiveValidators());
    assertThat(validators.getTotalSize()).isEqualTo(beaconState.getActiveValidators().size());
    assertThat(validators.validatorList.size())
        .isLessThanOrEqualTo(RestApiConstants.PAGE_SIZE_DEFAULT);
    assertThat(validators.getNextPageToken()).isLessThanOrEqualTo(PAGE_TOKEN_DEFAULT + 1);
  }

  @Test
  public void suppliedPageSizeParamIsUsed() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final int suppliedPageSizeParam = 10;

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(
            beaconState.getValidators(), suppliedPageSizeParam, PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.validatorList.size()).isEqualTo(suppliedPageSizeParam);
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT + 1);
  }

  @Test
  public void suppliedPageParamsAreUsed() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final int suppliedPageSizeParam = 9;
    final int suppliedPageTokenParam = 2;

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(
            beaconState.getValidators(), suppliedPageSizeParam, suppliedPageTokenParam);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(suppliedPageTokenParam + 1);
    assertThat(beaconValidators.validatorList.size()).isEqualTo(suppliedPageSizeParam);
  }

  @Test
  public void returnEmptyListIfPageParamsOutOfBounds() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final int suppliedPageSizeParam = 1000;
    final int suppliedPageTokenParam = 1000;

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(
            beaconState.getValidators(), suppliedPageSizeParam, suppliedPageTokenParam);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(suppliedPageSizeParam * suppliedPageTokenParam)
        .isGreaterThan(beaconValidators.validatorList.size());
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.validatorList.size()).isEqualTo(0);
  }

  @Test
  public void returnRemainderIfEdgeCasePageParams() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final List<Validator> validators = beaconState.getValidators();
    final int validatorsSize = validators.size();
    final int suppliedPageSizeParam = validatorsSize / 10 - 1;
    final int suppliedPageTokenParam = 11;
    final int expectedRemainderSize =
        validatorsSize - (suppliedPageSizeParam * suppliedPageTokenParam);
    assertThat(expectedRemainderSize).isLessThan(PAGE_SIZE_DEFAULT);
    assertThat(expectedRemainderSize).isGreaterThan(0);

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(validators, suppliedPageSizeParam, suppliedPageTokenParam);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.validatorList.size()).isEqualTo(expectedRemainderSize);
  }
}
