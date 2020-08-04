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

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class FlatFileSlashingProtection implements SlashingProtector {

  private final Map<BLSPublicKey, ValidatorSigningRecord> signingRecords = new HashMap<>();

  private final SyncDataAccessor dataAccessor;
  private final Path slashingProtectionBaseDir;

  public FlatFileSlashingProtection(
      final SyncDataAccessor dataAccessor, final Path slashingProtectionBaseDir) {
    this.dataAccessor = dataAccessor;
    this.slashingProtectionBaseDir = slashingProtectionBaseDir;
  }

  @Override
  public synchronized SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final UnsignedLong slot) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord = loadSigningRecord(validator);
          return handleResult(validator, signingRecord.maySignBlock(slot));
        });
  }

  @Override
  public synchronized SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final UnsignedLong sourceEpoch,
      final UnsignedLong targetEpoch) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord = loadSigningRecord(validator);
          return handleResult(validator, signingRecord.maySignAttestation(sourceEpoch, targetEpoch));
        });
  }

  private Boolean handleResult(
      final BLSPublicKey validator, final Optional<ValidatorSigningRecord> newRecord)
      throws IOException {
    if (newRecord.isEmpty()) {
      return false;
    }
    writeSigningRecord(validator, newRecord.get());
    return true;
  }

  private ValidatorSigningRecord loadSigningRecord(final BLSPublicKey validator)
      throws IOException {
    ValidatorSigningRecord record = signingRecords.get(validator);
    if (record != null) {
      return record;
    }
    record =
        dataAccessor
            .read(validatorRecordPath(validator))
            .map(ValidatorSigningRecord::fromBytes)
            .orElseGet(ValidatorSigningRecord::new);
    signingRecords.put(validator, record);
    return record;
  }

  private void writeSigningRecord(final BLSPublicKey validator, final ValidatorSigningRecord record)
      throws IOException {
    dataAccessor.syncedWrite(validatorRecordPath(validator), record.toBytes());
    signingRecords.put(validator, record);
  }

  private Path validatorRecordPath(final BLSPublicKey validator) {
    return slashingProtectionBaseDir.resolve(validator.toBytesCompressed().toUnprefixedHexString());
  }
}
