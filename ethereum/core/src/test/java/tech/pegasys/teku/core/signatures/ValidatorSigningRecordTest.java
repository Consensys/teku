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

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.core.signatures.record.ValidatorSigningRecord;

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
        new ValidatorSigningRecord(
            UnsignedLong.valueOf(10), UnsignedLong.valueOf(20), UnsignedLong.valueOf(30));
    final Bytes bytes = record.toBytes();
    final ValidatorSigningRecord result = ValidatorSigningRecord.fromBytes(bytes);
    assertThat(result).isEqualToComparingFieldByField(record);
  }

  @ParameterizedTest(name = "signBlock({0})")
  @MethodSource("blockCases")
  void signBlock(
      @SuppressWarnings("unused") final String name,
      final ValidatorSigningRecord input,
      final UnsignedLong slot,
      final Optional<ValidatorSigningRecord> expectedResult) {
    assertThat(input.maySignBlock(slot)).isEqualTo(expectedResult);
  }

  static List<Arguments> blockCases() {
    final ValidatorSigningRecord startingRecord =
        new ValidatorSigningRecord(
            UnsignedLong.valueOf(3), UnsignedLong.valueOf(6), UnsignedLong.valueOf(7));
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
        Arguments.of("=", startingRecord, UnsignedLong.valueOf(3), Optional.empty()),
        Arguments.of("<", startingRecord, UnsignedLong.valueOf(2), Optional.empty()),
        Arguments.of(">", startingRecord, UnsignedLong.valueOf(4), allowed(4, 6, 7)));
  }

  @ParameterizedTest(name = "maySignAttestation({0})")
  @MethodSource("attestationCases")
  void maySignAttestation(
      @SuppressWarnings("unused") final String name,
      final ValidatorSigningRecord input,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch,
      final Optional<ValidatorSigningRecord> expectedResult) {
    assertThat(input.maySignAttestation(sourceEpoch, targetEpoch)).isEqualTo(expectedResult);
  }

  static List<Arguments> attestationCases() {
    final ValidatorSigningRecord startingRecord =
        new ValidatorSigningRecord(ONE, UnsignedLong.valueOf(4), UnsignedLong.valueOf(6));
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
            UnsignedLong.valueOf(blockSlot),
            UnsignedLong.valueOf(sourceEpoch),
            UnsignedLong.valueOf(targetEpoch)));
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
        UnsignedLong.valueOf(sourceEpoch),
        UnsignedLong.valueOf(targetEpoch),
        expectedResult);
  }
}
