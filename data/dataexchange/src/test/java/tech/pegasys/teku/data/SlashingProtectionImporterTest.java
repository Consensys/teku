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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtectionImporterTest {
  private final ArgumentCaptor<String> stringArgs = ArgumentCaptor.forClass(String.class);
  private final String pubkey =
      "b845089a1457f811bfc000588fbb4e713669be8ce060ea6be3c6ece09afc3794106c91ca73acda5e5457122d58723bed";

  @Test
  public void shouldFailWithParseError() throws URISyntaxException, IOException {
    final String errorString = loadAndGetErrorText("minimal_invalidKey.json");
    assertThat(errorString).startsWith("Failed to load data from");
  }

  @Test
  public void shouldFailWithInvalidJson() throws URISyntaxException, IOException {
    final String errorString = loadAndGetErrorText("invalid_json.json");
    assertThat(errorString).startsWith("Json does not appear valid in file");
  }

  @Test
  public void shouldFailWithVersionCheckFailure() throws URISyntaxException, IOException {
    final String errorString = loadAndGetErrorText("oldMetadata.json");
    assertThat(errorString)
        .contains("Required version is " + Metadata.INTERCHANGE_VERSION.toString());
  }

  @Test
  public void shouldFailIfMetadataNotPresent() throws IOException, URISyntaxException {
    final String errorString = loadAndGetErrorText("signedBlock.json");
    assertThat(errorString).contains("does not appear to have metadata");
  }

  @Test
  public void shouldExportAndImportFile(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final SubCommandLogger logger = mock(SubCommandLogger.class);
    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();

    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    final File ruleFile = usingResourceFile("slashProtection.yml", tempDir);
    exporter.readSlashProtectionFile(ruleFile);
    final String originalFileContent = Files.readString(ruleFile.toPath());

    assertThat(Files.exists(ruleFile.toPath())).isTrue();
    assertThat(Files.exists(exportedFile)).isFalse();
    exporter.saveToFile(exportedFile.toString());
    ruleFile.delete();
    assertThat(Files.exists(exportedFile)).isTrue();
    assertThat(Files.exists(ruleFile.toPath())).isFalse();

    SlashingProtectionImporter importer =
        new SlashingProtectionImporter(logger, exportedFile.toString());
    importer.initialise(new File(exportedFile.toString()));
    importer.updateLocalRecords(tempDir);
    assertThat(Files.exists(ruleFile.toPath())).isTrue();

    assertThat(originalFileContent).isEqualTo(Files.readString(ruleFile.toPath()));
  }

  @Test
  void shouldImportFileOverRepairedRecords(@TempDir Path tempDir) throws Exception {
    final SubCommandLogger logger = mock(SubCommandLogger.class);
    final Path initialRecords = tempDir.resolve("initial");
    final Path repairedRecords = tempDir.resolve("repaired");
    assertThat(initialRecords.toFile().mkdirs()).isTrue();
    assertThat(repairedRecords.toFile().mkdirs()).isTrue();

    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();
    final File initialRuleFile =
        usingResourceFile("slashProtectionWithGenesisRoot.yml", initialRecords);
    final File repairedRuleFile =
        usingResourceFile("slashProtectionWithGenesisRoot.yml", repairedRecords);

    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    exporter.readSlashProtectionFile(repairedRuleFile);
    final String originalFileContent = Files.readString(initialRuleFile.toPath());

    assertThat(exportedFile).doesNotExist();
    exporter.saveToFile(exportedFile.toString());
    assertThat(exportedFile).exists();

    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(logger, repairedRecords, true);
    final UInt64 repairedSlot = UInt64.valueOf(1566);
    final UInt64 repairedEpoch = UInt64.valueOf(7668);
    repairer.updateRecords(repairedSlot, repairedEpoch);
    verify(logger, never()).error(any());
    verify(logger, never()).error(any(), any());
    assertThat(Files.readString(repairedRuleFile.toPath())).isNotEqualTo(originalFileContent);

    SlashingProtectionImporter importer =
        new SlashingProtectionImporter(logger, exportedFile.toString());
    importer.initialise(exportedFile.toFile());
    importer.updateLocalRecords(repairedRecords);

    // Should wind up with a file that contains the slot and epochs from the repair, combined with
    // the genesis root from the initial file
    final ValidatorSigningRecord initialRecord = loadSigningRecord(initialRuleFile);
    final ValidatorSigningRecord importedRecord = loadSigningRecord(repairedRuleFile);
    assertThat(importedRecord)
        .isEqualTo(
            new ValidatorSigningRecord(
                initialRecord.getGenesisValidatorsRoot(),
                repairedSlot,
                repairedEpoch,
                repairedEpoch));
  }

  private ValidatorSigningRecord loadSigningRecord(final File repairedRuleFile) throws IOException {
    return ValidatorSigningRecord.fromBytes(
        Bytes.wrap(Files.readAllBytes(repairedRuleFile.toPath())));
  }

  private String loadAndGetErrorText(final String resourceFile)
      throws URISyntaxException, IOException {
    final SubCommandLogger logger = mock(SubCommandLogger.class);
    SlashingProtectionImporter importer = new SlashingProtectionImporter(logger, resourceFile);

    importer.initialise(new File(Resources.getResource(resourceFile).toURI()));

    verify(logger).exit(eq(1), stringArgs.capture());
    return stringArgs.getValue();
  }

  private File usingResourceFile(final String resourceFileName, final Path tempDir)
      throws URISyntaxException, IOException {
    final Path tempFile = tempDir.resolve(pubkey + ".yml").toAbsolutePath();
    Files.copy(
        new File(Resources.getResource(resourceFileName).toURI()).toPath(),
        tempFile,
        StandardCopyOption.REPLACE_EXISTING);
    return tempFile.toFile();
  }
}
