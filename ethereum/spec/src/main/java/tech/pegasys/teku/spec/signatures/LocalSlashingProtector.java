/*
 * Copyright Consensys Software Inc., 2022
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

public class LocalSlashingProtector implements SlashingProtector {

  private static final Logger LOG = LogManager.getLogger();

  private final Map<BLSPublicKey, ValidatorSigningRecord> signingRecords =
      new ConcurrentHashMap<>();
  private final Map<BLSPublicKey, Path> slashingProtectionPath = new ConcurrentHashMap<>();
  private final Map<BLSPublicKey, ReentrantLock> validatorLocks = new ConcurrentHashMap<>();

  private final SyncDataAccessor dataAccessor;
  private final Path slashingProtectionBaseDir;

  private final boolean localSlashingProtectionSynchronizedModeEnabled;

  public LocalSlashingProtector(
      final SyncDataAccessor dataAccessor,
      final Path slashingProtectionBaseDir,
      boolean localSlashingProtectionSynchronizedModeEnabled) {
    this.dataAccessor = dataAccessor;
    this.slashingProtectionBaseDir = slashingProtectionBaseDir;
    this.localSlashingProtectionSynchronizedModeEnabled =
        localSlashingProtectionSynchronizedModeEnabled;
    if (!localSlashingProtectionSynchronizedModeEnabled) {
      LOG.info("Local slashing protection running with local locks enabled");
    }
  }

  @Override
  public SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot, final UInt64 slot) {
    return localSlashingProtectionSynchronizedModeEnabled
        ? maySignBlockSynchronized(validator, genesisValidatorsRoot, slot)
        : maySignBlockWithLocking(validator, genesisValidatorsRoot, slot);
  }

  @Override
  public SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final Bytes32 genesisValidatorsRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return localSlashingProtectionSynchronizedModeEnabled
        ? maySignAttestationSynchronized(validator, genesisValidatorsRoot, sourceEpoch, targetEpoch)
        : maySignAttestationWithLocking(validator, genesisValidatorsRoot, sourceEpoch, targetEpoch);
  }

  private SafeFuture<Boolean> maySignBlockWithLocking(
      BLSPublicKey validator, Bytes32 genesisValidatorsRoot, UInt64 slot) {
    return SafeFuture.of(
        () -> {
          final ReentrantLock lock = acquireLock(validator);
          try {
            final ValidatorSigningRecord signingRecord =
                loadOrCreateSigningRecord(validator, genesisValidatorsRoot);
            return handleResult(validator, signingRecord.maySignBlock(genesisValidatorsRoot, slot));
          } finally {
            lock.unlock();
          }
        });
  }

  private synchronized SafeFuture<Boolean> maySignBlockSynchronized(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot, final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord =
              loadOrCreateSigningRecord(validator, genesisValidatorsRoot);
          return handleResult(validator, signingRecord.maySignBlock(genesisValidatorsRoot, slot));
        });
  }

  private synchronized SafeFuture<Boolean> maySignAttestationSynchronized(
      final BLSPublicKey validator,
      final Bytes32 genesisValidatorsRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord =
              loadOrCreateSigningRecord(validator, genesisValidatorsRoot);
          return handleResult(
              validator,
              signingRecord.maySignAttestation(genesisValidatorsRoot, sourceEpoch, targetEpoch));
        });
  }

  private SafeFuture<Boolean> maySignAttestationWithLocking(
      final BLSPublicKey validator,
      final Bytes32 genesisValidatorsRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return SafeFuture.of(
        () -> {
          final ReentrantLock lock = acquireLock(validator);
          try {
            final ValidatorSigningRecord signingRecord =
                loadOrCreateSigningRecord(validator, genesisValidatorsRoot);
            return handleResult(
                validator,
                signingRecord.maySignAttestation(genesisValidatorsRoot, sourceEpoch, targetEpoch));
          } finally {
            lock.unlock();
          }
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

  @Override
  public Optional<ValidatorSigningRecord> getSigningRecord(final BLSPublicKey validator)
      throws IOException {
    final ValidatorSigningRecord record = signingRecords.get(validator);
    if (record != null) {
      return Optional.of(record);
    }
    final Optional<ValidatorSigningRecord> loaded =
        dataAccessor.read(validatorRecordPath(validator)).map(ValidatorSigningRecord::fromBytes);
    loaded.ifPresent(signingRecord -> signingRecords.put(validator, signingRecord));
    return loaded;
  }

  private ValidatorSigningRecord loadOrCreateSigningRecord(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot) throws IOException {
    final Optional<ValidatorSigningRecord> record = getSigningRecord(validator);
    return record.orElseGet(
        () -> {
          final ValidatorSigningRecord newRecord =
              new ValidatorSigningRecord(genesisValidatorsRoot);
          signingRecords.put(validator, newRecord);
          return newRecord;
        });
  }

  ReentrantLock acquireLock(final BLSPublicKey validator) {

    final ReentrantLock lock = validatorLocks.computeIfAbsent(validator, __ -> new ReentrantLock());
    lock.lock();
    return lock;
  }

  private void writeSigningRecord(final BLSPublicKey validator, final ValidatorSigningRecord record)
      throws IOException {
    dataAccessor.syncedWrite(validatorRecordPath(validator), record.toBytes());
    signingRecords.put(validator, record);
  }

  private Path validatorRecordPath(final BLSPublicKey validator) {
    return slashingProtectionPath.computeIfAbsent(
        validator,
        __ ->
            slashingProtectionBaseDir.resolve(
                validator.toBytesCompressed().toUnprefixedHexString() + ".yml"));
  }
}
