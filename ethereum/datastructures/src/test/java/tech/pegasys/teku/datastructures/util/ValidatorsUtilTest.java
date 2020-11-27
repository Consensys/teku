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

package tech.pegasys.teku.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Validator;

class ValidatorsUtilTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  void getValidatorIndex_shouldReturnValidatorIndex() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    assertThat(state.getValidators()).hasSizeGreaterThan(5);
    for (int i = 0; i < 5; i++) {
      final Validator validator = state.getValidators().get(i);
      assertThat(
              ValidatorsUtil.getValidatorIndex(
                  state, BLSPublicKey.fromBytesCompressed(validator.getPubkey())))
          .contains(i);
    }
  }

  @Test
  public void getValidatorIndex_shouldReturnEmptyWhenValidatorNotFound() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Optional<Integer> index =
        ValidatorsUtil.getValidatorIndex(state, dataStructureUtil.randomPublicKey());
    assertThat(index).isEmpty();
  }

  @Test
  public void getValidatorIndex_shouldReturnEmptyWhenValidatorInCacheButNotState() {
    // The public key to index cache is shared between all states (because validator indexes are
    // effectively set by the eth1 chain so are consistent across forks).
    // However we need to ensure we don't return a value added to the cache by a later state with
    // more validators, if the validator isn't actually in the target state.
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Validator validator = dataStructureUtil.randomValidator();
    final BeaconState nextState = state.updated(s -> s.getValidators().add(validator));

    assertThat(
            ValidatorsUtil.getValidatorIndex(
                nextState, BLSPublicKey.fromBytesCompressed(validator.getPubkey())))
        .contains(nextState.getValidators().size() - 1);
    assertThat(
            ValidatorsUtil.getValidatorIndex(
                state, BLSPublicKey.fromBytesCompressed(validator.getPubkey())))
        .isEmpty();
  }

  @Test
  public void getValidatorIndex_shouldNotCacheValidatorMissing() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    final Validator validator = dataStructureUtil.randomValidator();
    // Lookup the validator before it's in the list.
    assertThat(
            ValidatorsUtil.getValidatorIndex(
                state, BLSPublicKey.fromBytesCompressed(validator.getPubkey())))
        .isEmpty();

    // Then add it to the list and we should be able to find the index.
    final BeaconState nextState = state.updated(s -> s.getValidators().add(validator));
    assertThat(
            ValidatorsUtil.getValidatorIndex(
                nextState, BLSPublicKey.fromBytesCompressed(validator.getPubkey())))
        .contains(nextState.getValidators().size() - 1);
  }
}
