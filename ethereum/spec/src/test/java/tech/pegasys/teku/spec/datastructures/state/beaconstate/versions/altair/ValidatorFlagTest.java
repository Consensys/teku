/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;

public class ValidatorFlagTest {

  @Test
  public void isTimelyTarget() {
    assertThat(ValidatorFlag.isTimelyTarget(4)).isTrue();
    assertThat(ValidatorFlag.isTimelyTarget(5)).isTrue();
    assertThat(ValidatorFlag.isTimelyTarget(6)).isTrue();
    assertThat(ValidatorFlag.isTimelyTarget(7)).isTrue();
    assertThat(ValidatorFlag.isTimelyTarget(12)).isTrue();

    assertThat(ValidatorFlag.isTimelyTarget(1)).isFalse();
    assertThat(ValidatorFlag.isTimelyTarget(2)).isFalse();
    assertThat(ValidatorFlag.isTimelyTarget(3)).isFalse();
    assertThat(ValidatorFlag.isTimelyTarget(8)).isFalse();
  }

  @Test
  public void isAnyFlagSet() {
    assertThat(ValidatorFlag.isAnyFlagSet(1)).isTrue();
    assertThat(ValidatorFlag.isAnyFlagSet(2)).isTrue();
    assertThat(ValidatorFlag.isAnyFlagSet(3)).isTrue();
    assertThat(ValidatorFlag.isAnyFlagSet(4)).isTrue();
    assertThat(ValidatorFlag.isAnyFlagSet(5)).isTrue();
    assertThat(ValidatorFlag.isAnyFlagSet(6)).isTrue();
    assertThat(ValidatorFlag.isAnyFlagSet(7)).isTrue();
    assertThat(ValidatorFlag.isAnyFlagSet(9)).isTrue();

    assertThat(ValidatorFlag.isAnyFlagSet(0)).isFalse();
    assertThat(ValidatorFlag.isAnyFlagSet(8)).isFalse();
  }
}
