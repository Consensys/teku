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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;

public class StateValidatorsUtilTest {
  private final StateValidatorsUtil util = new StateValidatorsUtil();

  @Test
  public void shouldRaiseExceptionIfInputIsInvalid() {
    assertThrows(
        BadRequestException.class,
        () -> util.parseStatusFilter(Map.of("status", List.of("withdraw"))));
  }

  @ParameterizedTest
  @EnumSource(ValidatorStatus.class)
  public void shouldParseValidatorSource(final ValidatorStatus status) {
    assertThat(util.parseStatusFilter(Map.of("status", List.of(status.name()))))
        .containsExactly(status);
  }

  @Test
  public void shouldParseMultipleValidatorStatuses() {
    assertThat(
            util.parseStatusFilter(
                Map.of("status", List.of("active_ongoing", "active_exiting, withdrawal_done"))))
        .containsExactlyInAnyOrder(
            ValidatorStatus.active_ongoing,
            ValidatorStatus.active_exiting,
            ValidatorStatus.withdrawal_done);
  }
}
