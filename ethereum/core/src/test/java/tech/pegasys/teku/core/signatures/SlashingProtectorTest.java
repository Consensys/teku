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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.record.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionRecord;
import tech.pegasys.teku.data.slashinginterchange.YamlProvider;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SlashingProtectorTest {

  private static final UInt64 ATTESTATION_TEST_BLOCK_SLOT = UInt64.valueOf(3);
  private static final UInt64 BLOCK_TEST_SOURCE_EPOCH = UInt64.valueOf(12);
  private static final UInt64 BLOCK_TEST_TARGET_EPOCH = UInt64.valueOf(15);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final BLSPublicKey validator = dataStructureUtil.randomPublicKey();
  private final SyncDataAccessor dataWriter = mock(SyncDataAccessor.class);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final YamlProvider yamlProvider = new YamlProvider();

  private Path baseDir;
  private Path signingRecordPath;

  private SlashingProtector slashingProtectionStorage;

  @BeforeEach
  public void setup(@TempDir final Path tempDir) {
    this.baseDir = tempDir;
    signingRecordPath =
        baseDir.resolve(validator.toBytesCompressed().toUnprefixedHexString() + ".yml");
    slashingProtectionStorage = new SlashingProtector(baseDir);
  }

  @ParameterizedTest(name = "maySignBlock({0})")
  @MethodSource("blockCases")
  void maySignBlock(
      @SuppressWarnings("unused") final String name,
      final Optional<UInt64> lastSignedRecord,
      final UInt64 slot,
      final boolean allowed)
      throws Exception {
    writeSignedBlockRecord(lastSignedRecord);
    if (allowed) {
      assertBlockSigningAllowed(lastSignedRecord, slot);
    } else {
      assertBlockSigningDisallowed(slot);
    }
  }

  static List<Arguments> blockCases() {
    return List.of(
        Arguments.of("noExistingRecord", Optional.empty(), UInt64.valueOf(1), true),
        Arguments.of("=", Optional.of(UInt64.valueOf(3)), UInt64.valueOf(3), false),
        Arguments.of("<", Optional.of(UInt64.valueOf(3)), UInt64.valueOf(2), false),
        Arguments.of(">", Optional.of(UInt64.valueOf(3)), UInt64.valueOf(4), true));
  }

  @ParameterizedTest(name = "maySignAttestation({0})")
  @MethodSource("attestationCases")
  void maySignAttestation(
      @SuppressWarnings("unused") final String name,
      final Optional<ValidatorSigningRecord> lastSignedRecord,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch,
      final boolean allowed)
      throws Exception {

    writeValidatorSigningRecord(lastSignedRecord);
    if (allowed) {
      assertAttestationSigningAllowed(lastSignedRecord, sourceEpoch, targetEpoch);
    } else {
      assertAttestationSigningDisallowed(sourceEpoch, targetEpoch);
    }
  }

  static List<Arguments> attestationCases() {
    final Optional<ValidatorSigningRecord> existingRecord =
        Optional.of(
            new ValidatorSigningRecord(
                ATTESTATION_TEST_BLOCK_SLOT, UInt64.valueOf(4), UInt64.valueOf(6)));
    return List.of(
        // No record
        Arguments.of(
            "noExistingRecord", Optional.empty(), UInt64.valueOf(1), UInt64.valueOf(2), true),
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
        UInt64.valueOf(sourceEpoch),
        UInt64.valueOf(targetEpoch),
        allowed);
  }

  private void assertAttestationSigningAllowed(
      final Optional<ValidatorSigningRecord> lastSignedAttestation,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch)
      throws Exception {
    assertThat(
            slashingProtectionStorage.maySignAttestation(
                validator, forkInfo, sourceEpoch, targetEpoch))
        .isCompletedWithValue(true);

    final ValidatorSigningRecord updatedRecord =
        new ValidatorSigningRecord(
            lastSignedAttestation.isPresent() ? ATTESTATION_TEST_BLOCK_SLOT : UInt64.ZERO,
            sourceEpoch,
            targetEpoch);
    SlashingProtectionRecord record =
        yamlProvider.fileToObject(signingRecordPath.toFile(), SlashingProtectionRecord.class);
    assertThat(record.lastSignedAttestationSourceEpoch)
        .isEqualTo(updatedRecord.getAttestationSourceEpoch());
    assertThat(record.lastSignedBlockSlot).isEqualTo(updatedRecord.getBlockSlot());
    assertThat(record.lastSignedAttestationTargetEpoch)
        .isEqualTo(updatedRecord.getAttestationTargetEpoch());
  }

  private void assertAttestationSigningDisallowed(
      final UInt64 sourceEpoch, final UInt64 targetEpoch) {
    assertThat(
            slashingProtectionStorage.maySignAttestation(
                validator, forkInfo, sourceEpoch, targetEpoch))
        .isCompletedWithValue(false);
  }

  private void writeSignedBlockRecord(final Optional<UInt64> maybeSlot) throws IOException {
    if (maybeSlot.isEmpty()) {
      Optional<ValidatorSigningRecord> empty = Optional.empty();
      writeValidatorSigningRecord(empty);
    } else {
      ValidatorSigningRecord record =
          new ValidatorSigningRecord(
              maybeSlot.get(),
              ValidatorSigningRecord.NEVER_SIGNED,
              ValidatorSigningRecord.NEVER_SIGNED);
      writeValidatorSigningRecord(Optional.of(record));
    }
  }

  private void writeValidatorSigningRecord(
      final Optional<ValidatorSigningRecord> maybeValidatorSigningRecord) throws IOException {
    File file = signingRecordPath.toFile();
    if (maybeValidatorSigningRecord.isEmpty()) {
      if (file.exists() && file.isFile()) {
        file.delete();
      }
    } else {
      ValidatorSigningRecord data = maybeValidatorSigningRecord.get();
      SlashingProtectionRecord record =
          new SlashingProtectionRecord(
              data.getBlockSlot(),
              data.getAttestationSourceEpoch(),
              data.getAttestationTargetEpoch(),
              null);

      yamlProvider.writeToFile(file, record);
    }
  }

  private void assertBlockSigningAllowed(
      final Optional<UInt64> lastSignedBlockSlot, final UInt64 newBlockSlot) throws Exception {
    assertThat(slashingProtectionStorage.maySignBlock(validator, forkInfo, newBlockSlot))
        .isCompletedWithValue(true);

    final ValidatorSigningRecord updatedRecord =
        lastSignedBlockSlot.isPresent()
            ? blockTestSigningRecord(newBlockSlot)
            : new ValidatorSigningRecord(
                newBlockSlot,
                ValidatorSigningRecord.NEVER_SIGNED,
                ValidatorSigningRecord.NEVER_SIGNED);

    SlashingProtectionRecord record =
        yamlProvider.fileToObject(signingRecordPath.toFile(), SlashingProtectionRecord.class);
    assertThat(record.lastSignedBlockSlot).isEqualTo(updatedRecord.getBlockSlot());
  }

  private ValidatorSigningRecord blockTestSigningRecord(final UInt64 blockSlot) {
    return new ValidatorSigningRecord(blockSlot, BLOCK_TEST_SOURCE_EPOCH, BLOCK_TEST_TARGET_EPOCH);
  }

  private void assertBlockSigningDisallowed(final UInt64 newBlockSlot) throws Exception {
    assertThat(slashingProtectionStorage.maySignBlock(validator, forkInfo, newBlockSlot))
        .isCompletedWithValue(false);

    verify(dataWriter, never()).syncedWrite(any(), any());
  }
}
