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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.record.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionRecord;
import tech.pegasys.teku.data.slashinginterchange.YamlProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtector {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<BLSPublicKey, ValidatorSigningRecord> signingRecords = new HashMap<>();
  private final SyncDataAccessor dataAccessor;
  private final YamlProvider yamlProvider = new YamlProvider();
  private final Path slashingProtectionBaseDir;

  public SlashingProtector(
      final SyncDataAccessor dataAccessor, final Path slashingProtectionBaseDir) {
    this.slashingProtectionBaseDir = slashingProtectionBaseDir;
    this.dataAccessor = dataAccessor;
  }

  public synchronized SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot, final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord = loadSigningRecord(validator);
          return handleResult(validator, genesisValidatorsRoot, signingRecord.maySignBlock(slot));
        });
  }

  public synchronized SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final Bytes32 genesisValidatorsRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord = loadSigningRecord(validator);
          return handleResult(
              validator,
              genesisValidatorsRoot,
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
    record =
        dataAccessor
            .read(validatorRecordPath(validator))
            .map(
                val -> {
                  try {
                    return yamlProvider
                        .getObjectMapper()
                        .readValue(val.toArray(), SlashingProtectionRecord.class);
                  } catch (IOException e) {
                    LOG.trace(
                        "error parsing slashing protection file " + validatorRecordPath(validator),
                        e);
                  }
                  return new SlashingProtectionRecord();
                })
            .map(ValidatorSigningRecord::fromSlashingProtectionRecord)
            .orElseGet(ValidatorSigningRecord::new);

    signingRecords.put(validator, record);
    return record;
  }

  private void writeSigningRecord(
      final BLSPublicKey validator,
      final Bytes32 validatorsRoot,
      final ValidatorSigningRecord record)
      throws IOException {
    SlashingProtectionRecord slashingProtectionRecord =
        record.toSlashingProtectionRecord(validatorsRoot);
    dataAccessor.syncedWrite(
        validatorRecordPath(validator), yamlProvider.objectToYaml(slashingProtectionRecord));
    signingRecords.put(validator, record);
  }

  private Path validatorRecordPath(final BLSPublicKey validator) {
    return slashingProtectionBaseDir.resolve(
        validator.toBytesCompressed().toUnprefixedHexString() + ".yml");
  }
}
