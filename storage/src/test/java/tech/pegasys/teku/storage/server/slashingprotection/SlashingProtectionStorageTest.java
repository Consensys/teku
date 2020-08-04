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

package tech.pegasys.teku.storage.server.slashingprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.Database;

class SlashingProtectionStorageTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final BLSPublicKey validator = dataStructureUtil.randomPublicKey();
  private final Database database = Mockito.mock(Database.class);

  private final SlashingProtectionStorage slashingProtectionStorage =
      new SlashingProtectionStorage(database);

  @ParameterizedTest(name = "maySignBlock({0})")
  @MethodSource("blockCases")
  void maySignBlock(
      @SuppressWarnings("unused") final String name,
      final Optional<UnsignedLong> lastSignedRecord,
      final UnsignedLong slot,
      final boolean allowed) {
    if (allowed) {
      assertBlockSigningAllowed(lastSignedRecord, slot);
    } else {
      assertBlockSigningDisallowed(lastSignedRecord, slot);
    }
  }

  private static List<Arguments> blockCases() {
    return List.of(
        Arguments.of("noExistingRecord", Optional.empty(), UnsignedLong.valueOf(1), true),
        Arguments.of("=", Optional.of(UnsignedLong.valueOf(3)), UnsignedLong.valueOf(3), false),
        Arguments.of("<", Optional.of(UnsignedLong.valueOf(3)), UnsignedLong.valueOf(2), false),
        Arguments.of(">", Optional.of(UnsignedLong.valueOf(3)), UnsignedLong.valueOf(4), true));
  }

  @ParameterizedTest(name = "maySignAttestation({0})")
  @MethodSource("attestationCases")
  void maySignAttestation(
      @SuppressWarnings("unused") final String name,
      final Optional<SignedAttestationRecord> lastSignedRecord,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch,
      final boolean allowed) {
    if (allowed) {
      assertAttestationSigningAllowed(lastSignedRecord, sourceEpoch, targetEpoch);
    } else {
      assertAttestationSigningDisallowed(lastSignedRecord, sourceEpoch, targetEpoch);
    }
  }

  private static List<Arguments> attestationCases() {
    final Optional<SignedAttestationRecord> existingRecord =
        Optional.of(new SignedAttestationRecord(UnsignedLong.valueOf(4), UnsignedLong.valueOf(6)));
    return List.of(
        // No record
        Arguments.of(
            "noExistingRecord",
            Optional.empty(),
            UnsignedLong.valueOf(1),
            UnsignedLong.valueOf(2),
            true),
        attestationArguments("=", "=", existingRecord, 4, 6, false),
        attestationArguments("=", "<", existingRecord, 4, 5, false),
        attestationArguments("=", ">", existingRecord, 4, 7, true),
        attestationArguments("<", "=", existingRecord, 3, 6, false),
        attestationArguments("<", "<", existingRecord, 3, 5, false),
        attestationArguments("<", ">", existingRecord, 3, 7, false),
        attestationArguments(">", "=", existingRecord, 5, 6, false),
        attestationArguments(">", "<", existingRecord, 5, 5, false),
        attestationArguments(">", ">", existingRecord, 5, 7, true));
  }

  private static Arguments attestationArguments(
      final String sourceEpochDescription,
      final String targetEpochDescription,
      final Optional<SignedAttestationRecord> lastSignedRecord,
      final int sourceEpoch,
      final int targetEpoch,
      final boolean allowed) {
    return Arguments.of(
        "source " + sourceEpochDescription + ", target " + targetEpochDescription,
        lastSignedRecord,
        UnsignedLong.valueOf(sourceEpoch),
        UnsignedLong.valueOf(targetEpoch),
        allowed);
  }

  private void assertAttestationSigningAllowed(
      final Optional<SignedAttestationRecord> lastSignedAttestation,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch) {
    when(database.getLastSignedAttestationRecord(validator)).thenReturn(lastSignedAttestation);

    assertThat(slashingProtectionStorage.maySignAttestation(validator, sourceEpoch, targetEpoch))
        .isCompletedWithValue(true);
    verify(database)
        .recordLastSignedAttestation(
            validator, new SignedAttestationRecord(sourceEpoch, targetEpoch));
  }

  private void assertAttestationSigningDisallowed(
      final Optional<SignedAttestationRecord> lastSignedAttestation,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch) {
    when(database.getLastSignedAttestationRecord(validator)).thenReturn(lastSignedAttestation);

    assertThat(slashingProtectionStorage.maySignAttestation(validator, sourceEpoch, targetEpoch))
        .isCompletedWithValue(false);
    verify(database, never())
        .recordLastSignedAttestation(
            validator, new SignedAttestationRecord(sourceEpoch, targetEpoch));
  }

  private void assertBlockSigningAllowed(
      final Optional<UnsignedLong> lastSignedBlockSlot, final UnsignedLong newBlockSlot) {
    when(database.getLatestSignedBlockSlot(validator)).thenReturn(lastSignedBlockSlot);

    assertThat(slashingProtectionStorage.maySignBlock(validator, newBlockSlot))
        .isCompletedWithValue(true);

    verify(database).recordLastSignedBlock(validator, newBlockSlot);
  }

  private void assertBlockSigningDisallowed(
      final Optional<UnsignedLong> lastSignedBlockSlot, final UnsignedLong newBlockSlot) {
    when(database.getLatestSignedBlockSlot(validator)).thenReturn(lastSignedBlockSlot);

    assertThat(slashingProtectionStorage.maySignBlock(validator, newBlockSlot))
        .isCompletedWithValue(false);

    verify(database, never()).recordLastSignedBlock(validator, newBlockSlot);
  }
}
