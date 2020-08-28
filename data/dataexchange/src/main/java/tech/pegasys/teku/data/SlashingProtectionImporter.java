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
import tech.pegasys.teku.data.slashinginterchange.CompleteSigningHistory;
import tech.pegasys.teku.data.slashinginterchange.InterchangeFormat;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.MinimalSigningHistory;
import tech.pegasys.teku.data.slashinginterchange.SignedAttestation;
import tech.pegasys.teku.data.slashinginterchange.SignedBlock;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionRecord;
import tech.pegasys.teku.data.slashinginterchange.YamlProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class SlashingProtectionImporter {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final YamlProvider yamlProvider = new YamlProvider();
  private Path slashingProtectionPath;
  private List<MinimalSigningHistory> data = new ArrayList<>();
  private Metadata metadata;

  public void initialise(final File inputFile) throws IOException {
    final ObjectMapper jsonMapper = jsonProvider.getObjectMapper();
    JsonNode jsonNode = jsonMapper.readTree(inputFile);
    metadata = jsonMapper.treeToValue(jsonNode.get("metadata"), Metadata.class);
    if (!metadata.interchangeFormatVersion.equals(UInt64.ONE)) {
      System.err.println(
          "Import file " + inputFile.toString() + "Is not format version 1, cannot continue.");
      System.exit(1);
    }
    if (metadata.interchangeFormat.equals(InterchangeFormat.minimal)) {
      data =
          Arrays.asList(
              jsonMapper.treeToValue(jsonNode.get("data"), MinimalSigningHistory[].class));
    } else {
      summariseCompleteInterchangeFormat(
          Arrays.asList(
              jsonMapper.treeToValue(jsonNode.get("data"), CompleteSigningHistory[].class)));
    }
  }

  private void summariseCompleteInterchangeFormat(
      final List<CompleteSigningHistory> completeSigningData) {
    data =
        completeSigningData.stream()
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
    SlashingProtectionRecord record =
        new SlashingProtectionRecord(
            lastSlot.orElse(UInt64.ZERO),
            sourceEpoch.orElse(UInt64.MAX_VALUE),
            targetEpoch.orElse(UInt64.MAX_VALUE),
            metadata.genesisValidatorsRoot);
    return new MinimalSigningHistory(completeSigningHistory.pubkey, record);
  }

  public void updateLocalRecords(final Path slashingProtectionPath) {
    this.slashingProtectionPath = slashingProtectionPath;
    data.forEach(this::updateLocalRecord);
  }

  private void updateLocalRecord(final MinimalSigningHistory minimalSigningHistory) {
    String validatorString = minimalSigningHistory.pubkey.toHexString().substring(2).toLowerCase();

    System.out.println("Importing " + validatorString);
    Path outputFile = slashingProtectionPath.resolve(validatorString.concat(".yml"));
    Optional<SlashingProtectionRecord> existingRecord = Optional.empty();
    if (outputFile.toFile().exists()) {
      try {
        existingRecord =
            Optional.ofNullable(
                yamlProvider.fileToObject(outputFile.toFile(), SlashingProtectionRecord.class));
      } catch (IOException e) {
        System.err.println("Failed to read existing file: " + outputFile.toString());
        System.exit(1);
      }
    }
    if (existingRecord.isPresent()
        && metadata.genesisValidatorsRoot.compareTo(existingRecord.get().genesisValidatorsRoot)
            != 0) {
      System.err.println(
          "Validator "
              + minimalSigningHistory.pubkey.toHexString()
              + " has a different validators signing root to the data being imported");
      System.exit(1);
    }

    try {
      yamlProvider.writeToFile(
          outputFile.toFile(),
          minimalSigningHistory.toSlashingProtectionRecordMerging(
              existingRecord, metadata.genesisValidatorsRoot));
    } catch (IOException e) {
      System.err.println(
          "Validator " + minimalSigningHistory.pubkey.toHexString() + " was not updated.");
      System.exit(1);
    }
  }
}
