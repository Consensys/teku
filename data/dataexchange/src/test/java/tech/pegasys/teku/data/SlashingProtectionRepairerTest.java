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

package tech.pegasys.teku.data;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtectionRepairerTest {
  final ValidatorSigningRecord validatorSigningRecord =
      new ValidatorSigningRecord(null, ONE, ONE, ONE);
  private final UInt64 TWO = UInt64.valueOf(2);

  @Test
  void updateSigningRecord_shouldNotUpdateBetterRecords() {
    assertThat(
            SlashingProtectionRepairer.updateSigningRecord(
                ZERO, ZERO, Optional.of(validatorSigningRecord)))
        .isEqualTo(validatorSigningRecord);
  }

  @Test
  void updateSigningRecord_shouldNotRequireInitialRecord() {
    assertThat(SlashingProtectionRepairer.updateSigningRecord(ONE, ONE, Optional.empty()))
        .isEqualTo(validatorSigningRecord);
  }

  @Test
  void updateSigningRecord_shouldUpdateBlockSlot() {
    final ValidatorSigningRecord expectedValue = new ValidatorSigningRecord(null, TWO, ONE, ONE);
    assertThat(
            SlashingProtectionRepairer.updateSigningRecord(
                TWO, ZERO, Optional.of(validatorSigningRecord)))
        .isEqualTo(expectedValue);
  }

  @Test
  void updateSigningRecord_shouldUpdateAttestationEpoch() {
    final ValidatorSigningRecord expectedValue = new ValidatorSigningRecord(null, ONE, TWO, TWO);
    assertThat(
            SlashingProtectionRepairer.updateSigningRecord(
                ONE, TWO, Optional.of(validatorSigningRecord)))
        .isEqualTo(expectedValue);
  }

  @Test
  void updateSigningRecord_shouldUpdateSourceAttestationEpoch() {
    final ValidatorSigningRecord initialValue = new ValidatorSigningRecord(null, ONE, ZERO, TWO);
    final ValidatorSigningRecord expectedValue = new ValidatorSigningRecord(null, ONE, ONE, TWO);
    assertThat(SlashingProtectionRepairer.updateSigningRecord(ONE, ONE, Optional.of(initialValue)))
        .isEqualTo(expectedValue);
  }

  @Test
  void updateSigningRecord_shouldUpdateTargetAttestationEpoch() {
    final ValidatorSigningRecord initialValue = new ValidatorSigningRecord(null, ONE, TWO, ONE);
    final ValidatorSigningRecord expectedValue = new ValidatorSigningRecord(null, ONE, TWO, TWO);
    assertThat(SlashingProtectionRepairer.updateSigningRecord(ZERO, TWO, Optional.of(initialValue)))
        .isEqualTo(expectedValue);
  }
}
