/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.signatures;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class LocalSlashingProtectorConcurrentAccess implements SlashingProtector {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<BLSPublicKey, LocalSlashingProtectionRecord> records =
      new ConcurrentHashMap<>();
  private final SyncDataAccessor dataAccessor;
  private final Path slashingProtectionBaseDir;

  public LocalSlashingProtectorConcurrentAccess(
      final SyncDataAccessor dataAccessor, final Path slashingProtectionBaseDir) {
    this.dataAccessor = dataAccessor;
    this.slashingProtectionBaseDir = slashingProtectionBaseDir;
  }

  @Override
  public SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot, final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          final LocalSlashingProtectionRecord entry = acquireLock(validator, genesisValidatorsRoot);
          try {
            return entry.writeSigningRecord(
                dataAccessor, entry.maySignBlock(genesisValidatorsRoot, slot));
          } finally {
            entry.unlock();
          }
        });
  }

  @Override
  public SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final Bytes32 genesisValidatorsRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return SafeFuture.of(
        () -> {
          final LocalSlashingProtectionRecord entry = acquireLock(validator, genesisValidatorsRoot);
          try {
            return entry.writeSigningRecord(
                dataAccessor,
                entry.maySignAttestation(genesisValidatorsRoot, sourceEpoch, targetEpoch));
          } finally {
            entry.unlock();
          }
        });
  }

  @Override
  public Optional<ValidatorSigningRecord> getSigningRecord(final BLSPublicKey validator) {
    final Optional<LocalSlashingProtectionRecord> record = Optional.ofNullable(records.get(validator));
    if (record.isEmpty()) {
      // not loaded yet, just get from file if available, can create structure on use.
      return getValidatorSigningRecordFromFile(validator);
    }
    try {
      // unlike other occasions, we're not creating if absent, so only lock if present
      record.ifPresent(LocalSlashingProtectionRecord::lock);
      return record.map(LocalSlashingProtectionRecord::getSigningRecord);
    } finally {
      record.ifPresent(LocalSlashingProtectionRecord::unlock);
    }
  }

  LocalSlashingProtectionRecord acquireLock(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot) {
    final LocalSlashingProtectionRecord entry =
        records.computeIfAbsent(validator, __ -> addRecord(validator, genesisValidatorsRoot));
    // essential that we take a lock here
    entry.lock();
    return entry;
  }

  private LocalSlashingProtectionRecord addRecord(
      final BLSPublicKey publicKey, final Bytes32 genesisValidatorsRoot) {
    final Path slashingProtectedPath =
        slashingProtectionBaseDir.resolve(
            publicKey.toBytesCompressed().toUnprefixedHexString() + ".yml");
    final Optional<ValidatorSigningRecord> maybeRecord =
        getValidatorSigningRecordFromFile(publicKey);
    return new LocalSlashingProtectionRecord(
        slashingProtectedPath,
        maybeRecord.orElse(new ValidatorSigningRecord(genesisValidatorsRoot)),
        new ReentrantLock());
  }

  private Optional<ValidatorSigningRecord> getValidatorSigningRecordFromFile(
      final BLSPublicKey publicKey) {

    final Path slashingProtectedPath =
        slashingProtectionBaseDir.resolve(
            publicKey.toBytesCompressed().toUnprefixedHexString() + ".yml");
    try {
      return dataAccessor.read(slashingProtectedPath).map(ValidatorSigningRecord::fromBytes);
    } catch (IOException e) {
      LOG.error("Failed to load validator signing record {}", publicKey, e);
      return Optional.empty();
    }
  }

  @VisibleForTesting
  Optional<ReentrantLock> getLock(final BLSPublicKey validator) {
    return Optional.ofNullable(records.get(validator)).map(LocalSlashingProtectionRecord::getLock);
  }
}
