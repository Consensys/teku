/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.PublicKeyException;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtectionRepairer {
  private final List<SigningHistory> signingHistoryList = new ArrayList<>();
  private final Set<String> invalidRecords = new HashSet<>();
  private final SyncDataAccessor syncDataAccessor;
  private final List<String> errorList = new ArrayList<>();
  private final SubCommandLogger log;
  private Path slashingProtectionPath;
  private final boolean updateAllEnabled;

  public SlashingProtectionRepairer(
      final SubCommandLogger log, final boolean updateAllEnabled, final Path path) {
    this.log = log;
    this.updateAllEnabled = updateAllEnabled;
    this.syncDataAccessor = SyncDataAccessor.create(path);
  }

  public static SlashingProtectionRepairer create(
      final SubCommandLogger subCommandLog,
      final Path slashProtectionPath,
      final boolean updateAllEnabled) {
    final SlashingProtectionRepairer repairer =
        new SlashingProtectionRepairer(subCommandLog, updateAllEnabled, slashProtectionPath);
    repairer.initialise(slashProtectionPath);
    return repairer;
  }

  private void initialise(final Path slashProtectionPath) {
    this.slashingProtectionPath = slashProtectionPath;
    File slashingProtectionRecords = slashProtectionPath.toFile();
    Arrays.stream(slashingProtectionRecords.listFiles())
        .filter(file -> file.isFile() && file.getName().endsWith(".yml"))
        .forEach(this::readSlashProtectionFile);
  }

  private void readSlashProtectionFile(final File file) {
    final String filePrefix =
        file.getName().substring(0, file.getName().length() - ".yml".length());
    try {
      BLSPubKey pubkey = BLSPubKey.fromHexString(filePrefix);

      Optional<ValidatorSigningRecord> maybeRecord =
          syncDataAccessor.read(file.toPath()).map(ValidatorSigningRecord::fromBytes);
      if (maybeRecord.isEmpty() && invalidRecords.add(filePrefix)) {
        log.display(filePrefix + ": Empty slashing protection record");
        return;
      }

      if (updateAllEnabled) {
        log.display(filePrefix + ": looks valid");
      }
      ValidatorSigningRecord validatorSigningRecord = maybeRecord.get();
      signingHistoryList.add(new SigningHistory(pubkey, validatorSigningRecord));

    } catch (PublicKeyException e) {
      log.display(" --- " + file.getName() + " - invalid public key - ignoring file");
    } catch (Exception e) {
      if (invalidRecords.add(filePrefix)) {
        log.display(filePrefix + ": Incomplete or invalid slashing protection data");
      }
    }
  }

  public void updateRecords(final UInt64 slot, final UInt64 epoch) {
    invalidRecords.forEach(
        pubkey ->
            writeValidatorSigningRecord(
                new ValidatorSigningRecord(null, slot, epoch, epoch), pubkey));
    if (updateAllEnabled) {
      signingHistoryList.forEach(
          (historyRecord) -> updateValidatorSigningRecord(slot, epoch, historyRecord));
    }
    displayUpdateErrors();
  }

  private void updateValidatorSigningRecord(
      final UInt64 slot, final UInt64 epoch, final SigningHistory historyRecord) {
    final ValidatorSigningRecord currentRecord =
        historyRecord.toValidatorSigningRecord(Optional.empty(), null);
    final ValidatorSigningRecord updatedRecord =
        updateSigningRecord(slot, epoch, Optional.of(currentRecord));
    if (!currentRecord.equals(updatedRecord)) {
      writeValidatorSigningRecord(updatedRecord, toDisplayString(historyRecord.pubkey));
    }
  }

  private void writeValidatorSigningRecord(
      final ValidatorSigningRecord updatedRecord, final String pubkey) {
    log.display(pubkey + ": updating");
    Path outputFile = slashingProtectionPath.resolve(pubkey + ".yml");
    try {
      syncDataAccessor.syncedWrite(outputFile, updatedRecord.toBytes());
    } catch (IOException e) {
      errorList.add(pubkey + ": update failed, " + e.getMessage());
    }
  }

  private String toDisplayString(final BLSPubKey pubkey) {
    return pubkey.toBytes().toUnprefixedHexString().toLowerCase();
  }

  private void displayUpdateErrors() {
    if (errorList.isEmpty()) {
      return;
    }

    log.display("");
    log.display("The following errors were encountered:");
    for (String err : errorList) {
      log.display(err);
    }
  }

  public boolean hasUpdates() {
    if (updateAllEnabled) {
      return signingHistoryList.size() + invalidRecords.size() > 0;
    }
    return invalidRecords.size() > 0;
  }

  static ValidatorSigningRecord updateSigningRecord(
      final UInt64 blockSlot,
      final UInt64 attestationEpoch,
      final Optional<ValidatorSigningRecord> maybeRecord) {
    final UInt64 sourceEpoch =
        maybeRecord
            .map(ValidatorSigningRecord::getAttestationSourceEpoch)
            .orElse(attestationEpoch)
            .max(attestationEpoch);
    final UInt64 targetEpoch =
        maybeRecord
            .map(ValidatorSigningRecord::getAttestationTargetEpoch)
            .orElse(attestationEpoch)
            .max(attestationEpoch);
    final UInt64 slot =
        maybeRecord.map(ValidatorSigningRecord::getBlockSlot).orElse(blockSlot).max(blockSlot);
    return new ValidatorSigningRecord(
        maybeRecord.map(ValidatorSigningRecord::getGenesisValidatorsRoot).orElse(null),
        slot,
        sourceEpoch,
        targetEpoch);
  }
}
