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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

class SlashingProtectorTest {

  private static final UnsignedLong ATTESTATION_TEST_BLOCK_SLOT = UnsignedLong.valueOf(3);
  private static final UnsignedLong BLOCK_TEST_SOURCE_EPOCH = UnsignedLong.valueOf(12);
  private static final UnsignedLong BLOCK_TEST_TARGET_EPOCH = UnsignedLong.valueOf(15);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final BLSPublicKey validator = dataStructureUtil.randomPublicKey();
  private final SyncDataAccessor dataWriter = mock(SyncDataAccessor.class);

  private final Path baseDir = Path.of("/data");
  private final Path signingRecordPath =
      baseDir.resolve(validator.toBytesCompressed().toUnprefixedHexString());

  private final SlashingProtector slashingProtectionStorage =
      new SlashingProtector(dataWriter, baseDir);

  @ParameterizedTest(name = "maySignBlock({0})")
  @MethodSource("blockCases")
  void maySignBlock(
      @SuppressWarnings("unused") final String name,
      final Optional<UnsignedLong> lastSignedRecord,
      final UnsignedLong slot,
      final boolean allowed)
      throws Exception {
    if (allowed) {
      assertBlockSigningAllowed(lastSignedRecord, slot);
    } else {
      assertBlockSigningDisallowed(lastSignedRecord, slot);
    }
  }

  static List<Arguments> blockCases() {
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
      final Optional<ValidatorSigningRecord> lastSignedRecord,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch,
      final boolean allowed)
      throws Exception {
    if (allowed) {
      assertAttestationSigningAllowed(lastSignedRecord, sourceEpoch, targetEpoch);
    } else {
      assertAttestationSigningDisallowed(lastSignedRecord, sourceEpoch, targetEpoch);
    }
  }

  static List<Arguments> attestationCases() {
    final Optional<ValidatorSigningRecord> existingRecord =
        Optional.of(
            new ValidatorSigningRecord(
                ATTESTATION_TEST_BLOCK_SLOT, UnsignedLong.valueOf(4), UnsignedLong.valueOf(6)));
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
      final Optional<ValidatorSigningRecord> lastSignedRecord,
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
      final Optional<ValidatorSigningRecord> lastSignedAttestation,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch)
      throws Exception {
    when(dataWriter.read(signingRecordPath))
        .thenReturn(lastSignedAttestation.map(ValidatorSigningRecord::toBytes));

    assertThat(slashingProtectionStorage.maySignAttestation(validator, sourceEpoch, targetEpoch))
        .isCompletedWithValue(true);

    final ValidatorSigningRecord updatedRecord =
        new ValidatorSigningRecord(
            lastSignedAttestation.isPresent() ? ATTESTATION_TEST_BLOCK_SLOT : UnsignedLong.ZERO,
            sourceEpoch,
            targetEpoch);
    verify(dataWriter).syncedWrite(signingRecordPath, updatedRecord.toBytes());
  }

  private void assertAttestationSigningDisallowed(
      final Optional<ValidatorSigningRecord> lastSignedAttestation,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch)
      throws IOException {
    when(dataWriter.read(signingRecordPath))
        .thenReturn(lastSignedAttestation.map(ValidatorSigningRecord::toBytes));

    assertThat(slashingProtectionStorage.maySignAttestation(validator, sourceEpoch, targetEpoch))
        .isCompletedWithValue(false);
    verify(dataWriter, never()).syncedWrite(any(), any());
  }

  private void assertBlockSigningAllowed(
      final Optional<UnsignedLong> lastSignedBlockSlot, final UnsignedLong newBlockSlot)
      throws Exception {
    when(dataWriter.read(signingRecordPath))
        .thenReturn(lastSignedBlockSlot.map(this::blockTestSigningRecord));

    assertThat(slashingProtectionStorage.maySignBlock(validator, newBlockSlot))
        .isCompletedWithValue(true);

    final Bytes updatedRecord =
        lastSignedBlockSlot.isPresent()
            ? blockTestSigningRecord(newBlockSlot)
            : new ValidatorSigningRecord(
                    newBlockSlot,
                    ValidatorSigningRecord.NEVER_SIGNED,
                    ValidatorSigningRecord.NEVER_SIGNED)
                .toBytes();
    verify(dataWriter).syncedWrite(signingRecordPath, updatedRecord);
  }

  private Bytes blockTestSigningRecord(final UnsignedLong blockSlot) {
    return new ValidatorSigningRecord(blockSlot, BLOCK_TEST_SOURCE_EPOCH, BLOCK_TEST_TARGET_EPOCH)
        .toBytes();
  }

  private void assertBlockSigningDisallowed(
      final Optional<UnsignedLong> lastSignedBlockSlot, final UnsignedLong newBlockSlot)
      throws Exception {
    when(dataWriter.read(signingRecordPath))
        .thenReturn(lastSignedBlockSlot.map(this::blockTestSigningRecord));

    assertThat(slashingProtectionStorage.maySignBlock(validator, newBlockSlot))
        .isCompletedWithValue(false);

    verify(dataWriter, never()).syncedWrite(any(), any());
  }
}
