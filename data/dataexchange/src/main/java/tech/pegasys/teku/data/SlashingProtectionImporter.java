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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.SignedAttestation;
import tech.pegasys.teku.data.slashinginterchange.SignedBlock;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class SlashingProtectionImporter {
  private final JsonProvider jsonProvider = new JsonProvider();
  private Path slashingProtectionPath;
  private List<SigningHistory> data = new ArrayList<>();
  private Metadata metadata;
  private final SubCommandLogger log;
  private final SyncDataAccessor syncDataAccessor;

  public SlashingProtectionImporter(final SubCommandLogger log, final String path) {
    this.log = log;
    syncDataAccessor = SyncDataAccessor.create(Paths.get(path));
  }

  public void initialise(final String inputString) throws IOException {
    final ObjectMapper jsonMapper = jsonProvider.getObjectMapper();
    try {
      final JsonNode jsonNode = jsonMapper.readTree(inputString);

      processJsonData("string input", jsonMapper, jsonNode);

    } catch (JsonMappingException e) {
      String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      log.exit(1, "Failed to load data from string input. " + cause);
    } catch (JsonParseException e) {
      String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      log.exit(1, "Json does not appear valid in string input. " + cause);
    }
  }

  public void initialise(final File inputFile) throws IOException {
    final ObjectMapper jsonMapper = jsonProvider.getObjectMapper();
    try {
      final JsonNode jsonNode = jsonMapper.readTree(inputFile);

      processJsonData("file " + inputFile.toString(), jsonMapper, jsonNode);

    } catch (JsonMappingException e) {
      String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      log.exit(1, "Failed to load data from " + inputFile.getName() + ". " + cause);
    } catch (JsonParseException e) {
      String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      log.exit(1, "Json does not appear valid in file " + inputFile.getName() + ". " + cause);
    }
  }

  private void processJsonData(
      final String importFromMessage, final ObjectMapper jsonMapper, final JsonNode jsonNode)
      throws JsonProcessingException {
    metadata = jsonMapper.treeToValue(jsonNode.get("metadata"), Metadata.class);
    if (metadata == null) {
      log.exit(
          1,
          "Import "
              + importFromMessage
              + " does not appear to have metadata information, and cannot be loaded.");
      return; // Testing mocks log.exit
    }
    if (!INTERCHANGE_VERSION.equals(UInt64.valueOf(4))
        && !INTERCHANGE_VERSION.equals(metadata.interchangeFormatVersion)) {
      log.exit(
          1,
          "Import "
              + importFromMessage
              + " has unsupported format version "
              + metadata.interchangeFormatVersion
              + ". Required version is "
              + INTERCHANGE_VERSION);
      return; // Testing mocks log.exit
    }

    data =
        summariseCompleteInterchangeFormat(
            Arrays.asList(jsonMapper.treeToValue(jsonNode.get("data"), SigningHistory[].class)));
  }

  private List<SigningHistory> summariseCompleteInterchangeFormat(
      final List<SigningHistory> completeSigningData) {
    return completeSigningData.stream()
        .map(this::signingHistoryConverter)
        .collect(Collectors.toList());
  }

  private SigningHistory signingHistoryConverter(final SigningHistory signingHistory) {
    try {
      final Optional<UInt64> lastSlot =
          signingHistory.signedBlocks.stream()
              .map(SignedBlock::getSlot)
              .filter(Objects::nonNull)
              .max(UInt64::compareTo);
      final Optional<UInt64> sourceEpoch =
          signingHistory.signedAttestations.stream()
              .map(SignedAttestation::getSourceEpoch)
              .filter(Objects::nonNull)
              .max(UInt64::compareTo);
      final Optional<UInt64> targetEpoch =
          signingHistory.signedAttestations.stream()
              .map(SignedAttestation::getTargetEpoch)
              .filter(Objects::nonNull)
              .max(UInt64::compareTo);
      final ValidatorSigningRecord record =
          new ValidatorSigningRecord(
              metadata.genesisValidatorsRoot,
              lastSlot.orElse(UInt64.ZERO),
              sourceEpoch.orElse(ValidatorSigningRecord.NEVER_SIGNED),
              targetEpoch.orElse(ValidatorSigningRecord.NEVER_SIGNED));
      return new SigningHistory(signingHistory.pubkey, record);
    } catch (NullPointerException e) {
      System.out.println(signingHistory.pubkey);
      throw e;
    }
  }

  public void updateLocalRecords(final Path slashingProtectionPath) {
    this.slashingProtectionPath = slashingProtectionPath;
    data.forEach(this::updateLocalRecord);
    log.display("Updated " + data.size() + " validator slashing protection records");
  }

  private void updateLocalRecord(final SigningHistory signingHistory) {
    String validatorString = signingHistory.pubkey.toBytes().toUnprefixedHexString().toLowerCase();

    log.display("Importing " + validatorString);
    Path outputFile = slashingProtectionPath.resolve(validatorString + ".yml");
    Optional<ValidatorSigningRecord> existingRecord = Optional.empty();
    if (outputFile.toFile().exists()) {
      try {
        existingRecord = syncDataAccessor.read(outputFile).map(ValidatorSigningRecord::fromBytes);
      } catch (IOException e) {
        log.exit(1, "Failed to read existing file: " + outputFile);
      }
    }
    if (existingRecord.isPresent()
        && existingRecord.get().getGenesisValidatorsRoot() != null
        && metadata.genesisValidatorsRoot != null
        && metadata.genesisValidatorsRoot.compareTo(existingRecord.get().getGenesisValidatorsRoot())
            != 0) {
      log.exit(
          1,
          "Validator "
              + signingHistory.pubkey.toHexString()
              + " has a different validators signing root to the data being imported");
    }

    try {
      syncDataAccessor.syncedWrite(
          outputFile,
          signingHistory
              .toValidatorSigningRecord(existingRecord, metadata.genesisValidatorsRoot)
              .toBytes());
    } catch (IOException e) {
      log.exit(1, "Validator " + signingHistory.pubkey.toHexString() + " was not updated.");
    }
  }
}
