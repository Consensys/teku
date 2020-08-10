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

package tech.pegasys.teku.core.signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.core.signatures.record.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ValidatorSigningRecordTest {

  @Test
  void shouldRoundTripDefaultValuesToBytes() {
    final ValidatorSigningRecord record = new ValidatorSigningRecord();
    final Bytes bytes = record.toBytes();
    final ValidatorSigningRecord result = ValidatorSigningRecord.fromBytes(bytes);
    assertThat(result).isEqualToComparingFieldByField(record);
  }

  @Test
  void shouldRoundTripToBytes() {
    final ValidatorSigningRecord record =
        new ValidatorSigningRecord(UInt64.valueOf(10), UInt64.valueOf(20), UInt64.valueOf(30));
    final Bytes bytes = record.toBytes();
    final ValidatorSigningRecord result = ValidatorSigningRecord.fromBytes(bytes);
    assertThat(result).isEqualToComparingFieldByField(record);
  }

  @ParameterizedTest(name = "signBlock({0})")
  @MethodSource("blockCases")
  void signBlock(
      @SuppressWarnings("unused") final String name,
      final ValidatorSigningRecord input,
      final UInt64 slot,
      final Optional<ValidatorSigningRecord> expectedResult) {
    assertThat(input.maySignBlock(slot)).isEqualTo(expectedResult);
  }

  static List<Arguments> blockCases() {
    final ValidatorSigningRecord startingRecord =
        new ValidatorSigningRecord(UInt64.valueOf(3), UInt64.valueOf(6), UInt64.valueOf(7));
    return List.of(
        Arguments.of(
            "noExistingRecord",
            new ValidatorSigningRecord(),
            ONE,
            Optional.of(
                new ValidatorSigningRecord(
                    ONE,
                    ValidatorSigningRecord.NEVER_SIGNED,
                    ValidatorSigningRecord.NEVER_SIGNED))),
        Arguments.of("=", startingRecord, UInt64.valueOf(3), Optional.empty()),
        Arguments.of("<", startingRecord, UInt64.valueOf(2), Optional.empty()),
        Arguments.of(">", startingRecord, UInt64.valueOf(4), allowed(4, 6, 7)));
  }

  @ParameterizedTest(name = "maySignAttestation({0})")
  @MethodSource("attestationCases")
  void maySignAttestation(
      @SuppressWarnings("unused") final String name,
      final ValidatorSigningRecord input,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch,
      final Optional<ValidatorSigningRecord> expectedResult) {
    assertThat(input.maySignAttestation(sourceEpoch, targetEpoch)).isEqualTo(expectedResult);
  }

  static List<Arguments> attestationCases() {
    final ValidatorSigningRecord startingRecord =
        new ValidatorSigningRecord(ONE, UInt64.valueOf(4), UInt64.valueOf(6));
    return List.of(
        // No record
        attestationArguments(
            "NEVER_SIGNED", "NEVER_SIGNED", new ValidatorSigningRecord(), 1, 2, allowed(0, 1, 2)),
        attestationArguments("=", "=", startingRecord, 4, 6, disallowed()),
        attestationArguments("=", "<", startingRecord, 4, 5, disallowed()),
        attestationArguments("=", ">", startingRecord, 4, 7, allowed(1, 4, 7)),
        attestationArguments("<", "=", startingRecord, 3, 6, disallowed()),
        attestationArguments("<", "<", startingRecord, 3, 5, disallowed()),
        attestationArguments("<", ">", startingRecord, 3, 7, disallowed()),
        attestationArguments(">", "=", startingRecord, 5, 6, disallowed()),
        attestationArguments(">", "<", startingRecord, 5, 5, disallowed()),
        attestationArguments(">", ">", startingRecord, 5, 7, allowed(1, 5, 7)));
  }

  private static Optional<ValidatorSigningRecord> disallowed() {
    return Optional.empty();
  }

  private static Optional<ValidatorSigningRecord> allowed(
      final int blockSlot, final int sourceEpoch, final int targetEpoch) {
    return Optional.of(
        new ValidatorSigningRecord(
            UInt64.valueOf(blockSlot), UInt64.valueOf(sourceEpoch), UInt64.valueOf(targetEpoch)));
  }

  private static Arguments attestationArguments(
      final String sourceEpochDescription,
      final String targetEpochDescription,
      final ValidatorSigningRecord lastSignedRecord,
      final int sourceEpoch,
      final int targetEpoch,
      final Optional<ValidatorSigningRecord> expectedResult) {
    return Arguments.of(
        "source " + sourceEpochDescription + ", target " + targetEpochDescription,
        lastSignedRecord,
        UInt64.valueOf(sourceEpoch),
        UInt64.valueOf(targetEpoch),
        expectedResult);
  }
}
