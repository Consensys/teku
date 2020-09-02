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

package tech.pegasys.teku.data;

import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.CompleteSigningHistory;
import tech.pegasys.teku.data.slashinginterchange.InterchangeFormat;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.MinimalSigningHistory;
import tech.pegasys.teku.data.slashinginterchange.SignedAttestation;
import tech.pegasys.teku.data.slashinginterchange.SignedBlock;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.logging.SubCommandLogger;
import tech.pegasys.teku.provider.JsonProvider;

public class SlashingProtectionImporter {
  private final JsonProvider jsonProvider = new JsonProvider();
  private Path slashingProtectionPath;
  private List<MinimalSigningHistory> data = new ArrayList<>();
  private Metadata metadata;
  private final SubCommandLogger log;
  private final SyncDataAccessor syncDataAccessor = new SyncDataAccessor();

  public SlashingProtectionImporter(final SubCommandLogger log) {
    this.log = log;
  }

  public void initialise(final File inputFile) throws IOException {
    final ObjectMapper jsonMapper = jsonProvider.getObjectMapper();
    try {
      final JsonNode jsonNode = jsonMapper.readTree(inputFile);

      metadata = jsonMapper.treeToValue(jsonNode.get("metadata"), Metadata.class);
      if (metadata == null) {
        log.exit(
            1,
            "Import file "
                + inputFile.toString()
                + " does not appear to have metadata information, and cannot be loaded.");
        return; // Testing mocks log.exit
      }
      if (!metadata.interchangeFormatVersion.equals(INTERCHANGE_VERSION)) {
        log.exit(
            1,
            "Import file "
                + inputFile.toString()
                + " is not format version "
                + INTERCHANGE_VERSION.toString()
                + ", cannot continue.");
        return; // Testing mocks log.exit
      }

      if (metadata.interchangeFormat.equals(InterchangeFormat.minimal)) {
        data =
            Arrays.asList(
                jsonMapper.treeToValue(jsonNode.get("data"), MinimalSigningHistory[].class));
      } else {
        data =
            summariseCompleteInterchangeFormat(
                Arrays.asList(
                    jsonMapper.treeToValue(jsonNode.get("data"), CompleteSigningHistory[].class)));
      }
    } catch (JsonMappingException e) {
      String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      log.exit(1, "Failed to load data from " + inputFile.getName() + ". " + cause);
    } catch (JsonParseException e) {
      String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      log.exit(1, "Json does not appear valid in file " + inputFile.getName() + ". " + cause);
    }
  }

  private List<MinimalSigningHistory> summariseCompleteInterchangeFormat(
      final List<CompleteSigningHistory> completeSigningData) {
    return completeSigningData.stream()
        .map(this::minimalSigningHistoryConverter)
        .collect(Collectors.toList());
  }

  private MinimalSigningHistory minimalSigningHistoryConverter(
      final CompleteSigningHistory completeSigningHistory) {
    final Optional<UInt64> lastSlot =
        completeSigningHistory.signedBlocks.stream()
            .map(SignedBlock::getSlot)
            .max(UInt64::compareTo);
    final Optional<UInt64> sourceEpoch =
        completeSigningHistory.signedAttestations.stream()
            .map(SignedAttestation::getSourceEpoch)
            .max(UInt64::compareTo);
    final Optional<UInt64> targetEpoch =
        completeSigningHistory.signedAttestations.stream()
            .map(SignedAttestation::getTargetEpoch)
            .max(UInt64::compareTo);
    final ValidatorSigningRecord record =
        new ValidatorSigningRecord(
            metadata.genesisValidatorsRoot,
            lastSlot.orElse(UInt64.ZERO),
            sourceEpoch.orElse(ValidatorSigningRecord.NEVER_SIGNED),
            targetEpoch.orElse(ValidatorSigningRecord.NEVER_SIGNED));
    return new MinimalSigningHistory(completeSigningHistory.pubkey, record);
  }

  public void updateLocalRecords(final Path slashingProtectionPath) {
    this.slashingProtectionPath = slashingProtectionPath;
    data.forEach(this::updateLocalRecord);
    log.display("Updated " + data.size() + " validator slashing protection records");
  }

  private void updateLocalRecord(final MinimalSigningHistory minimalSigningHistory) {
    String validatorString = minimalSigningHistory.pubkey.toHexString().substring(2).toLowerCase();

    log.display("Importing " + validatorString);
    Path outputFile = slashingProtectionPath.resolve(validatorString + ".yml");
    Optional<ValidatorSigningRecord> existingRecord = Optional.empty();
    if (outputFile.toFile().exists()) {
      try {
        existingRecord = syncDataAccessor.read(outputFile).map(ValidatorSigningRecord::fromBytes);
      } catch (IOException e) {
        log.exit(1, "Failed to read existing file: " + outputFile.toString());
      }
    }
    if (existingRecord.isPresent()
        && metadata.genesisValidatorsRoot.compareTo(existingRecord.get().getGenesisValidatorsRoot())
            != 0) {
      log.exit(
          1,
          "Validator "
              + minimalSigningHistory.pubkey.toHexString()
              + " has a different validators signing root to the data being imported");
    }

    try {
      syncDataAccessor.syncedWrite(
          outputFile,
          minimalSigningHistory
              .toValidatorSigningRecord(existingRecord, metadata.genesisValidatorsRoot)
              .toBytes());
    } catch (IOException e) {
      log.exit(1, "Validator " + minimalSigningHistory.pubkey.toHexString() + " was not updated.");
    }
  }
}
