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

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.record.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionRecord;
import tech.pegasys.teku.data.slashinginterchange.YamlProvider;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtector {
  private final Map<BLSPublicKey, ValidatorSigningRecord> signingRecords = new HashMap<>();
  YamlProvider yamlProvider = new YamlProvider();
  private final Path slashingProtectionBaseDir;

  public SlashingProtector(final Path slashingProtectionBaseDir) {
    this.slashingProtectionBaseDir = slashingProtectionBaseDir;
  }

  public synchronized SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final ForkInfo forkInfo, final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord = loadSigningRecord(validator);
          return handleResult(
              validator, forkInfo.getGenesisValidatorsRoot(), signingRecord.maySignBlock(slot));
        });
  }

  public synchronized SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final ForkInfo forkInfo,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord = loadSigningRecord(validator);
          return handleResult(
              validator,
              forkInfo.getGenesisValidatorsRoot(),
              signingRecord.maySignAttestation(sourceEpoch, targetEpoch));
        });
  }

  private Boolean handleResult(
      final BLSPublicKey validator,
      final Bytes32 validatorsRoot,
      final Optional<ValidatorSigningRecord> newRecord)
      throws IOException {
    if (newRecord.isEmpty()) {
      return false;
    }
    writeSigningRecord(validator, validatorsRoot, newRecord.get());
    return true;
  }

  private ValidatorSigningRecord loadSigningRecord(final BLSPublicKey validator)
      throws IOException {
    ValidatorSigningRecord record = signingRecords.get(validator);
    if (record != null) {
      return record;
    }
    if (!validatorRecordPath(validator).toFile().exists()) {
      return new ValidatorSigningRecord();
    }
    SlashingProtectionRecord slashingProtectionRecord =
        yamlProvider.fileToObject(
            validatorRecordPath(validator).toFile(), SlashingProtectionRecord.class);
    record =
        new ValidatorSigningRecord(
            slashingProtectionRecord.lastSignedBlockSlot,
            slashingProtectionRecord.lastSignedAttestationSourceEpoch,
            slashingProtectionRecord.lastSignedAttestationTargetEpoch);

    signingRecords.put(validator, record);
    return record;
  }

  private void writeSigningRecord(
      final BLSPublicKey validator,
      final Bytes32 validatorsRoot,
      final ValidatorSigningRecord record)
      throws IOException {
    SlashingProtectionRecord slashingProtectionRecord =
        new SlashingProtectionRecord(
            record.getBlockSlot(),
            record.getAttestationSourceEpoch(),
            record.getAttestationTargetEpoch(),
            validatorsRoot);
    yamlProvider.writeToFile(validatorRecordPath(validator).toFile(), slashingProtectionRecord);
    signingRecords.put(validator, record);
  }

  private Path validatorRecordPath(final BLSPublicKey validator) {
    return slashingProtectionBaseDir.resolve(
        validator.toBytesCompressed().toUnprefixedHexString() + ".yml");
  }
}
