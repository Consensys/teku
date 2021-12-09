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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.cli.OSUtils;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.SignedBlock;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionInterchangeFormat;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class SlashingProtectionExporterTest {
  private static final Logger LOG = LogManager.getLogger();
  final List<String> log = new ArrayList<>();
  private final JsonProvider jsonProvider = new JsonProvider();
  private final String pubkey =
      "b845089a1457f811bfc000588fbb4e713669be8ce060ea6be3c6ece09afc3794106c91ca73acda5e5457122d58723bed";
  private final Bytes32 validatorsRoot =
      Bytes32.fromHexString("0x6e2c5d8a89dfe121a92c8812bea69fe9f84ae48f63aafe34ef7e18c7eac9af70");

  @Test
  public void shouldReadSlashingProtectionFile_withEmptyGenesisValidatorsRoot(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    Optional<String> error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtection.yml", tempDir), log::add);
    assertThat(log).containsExactly("Exporting " + pubkey);
    assertThat(error).isEmpty();

    final SlashingProtectionInterchangeFormat parsedData =
        jsonProvider.jsonToObject(
            exporter.getPrettyJson(), SlashingProtectionInterchangeFormat.class);
    final SlashingProtectionInterchangeFormat expectedData = getExportData(null, 327, 51, 1741);
    assertThat(parsedData).isEqualTo(expectedData);
  }

  @Test
  public void shouldReadSlashingProtectionFile_withGenesisValidatorsRoot(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    Optional<String> error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtectionWithGenesisRoot.yml", tempDir), log::add);
    assertThat(log).containsExactly("Exporting " + pubkey);
    assertThat(error).isEmpty();

    final SlashingProtectionInterchangeFormat parsedData =
        jsonProvider.jsonToObject(
            exporter.getPrettyJson(), SlashingProtectionInterchangeFormat.class);
    final SlashingProtectionInterchangeFormat expectedData =
        getExportData(validatorsRoot, 327, 51, 1741);
    assertThat(parsedData).isEqualTo(expectedData);
  }

  @Test
  public void shouldReadFilesWithEmptyRootAfterGenesisRootIsDefined(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    Optional<String> error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtectionWithGenesisRoot.yml", tempDir), log::add);
    assertThat(error).isEmpty();
    error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtection.yml", tempDir), log::add);
    assertThat(error).isEmpty();

    assertThat(log).containsExactly("Exporting " + pubkey, "Exporting " + pubkey);
  }

  @Test
  public void shouldReadFileWithGenesisRootDefinedSecond(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    Optional<String> error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtection.yml", tempDir), log::add);
    assertThat(error).isEmpty();
    error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtectionWithGenesisRoot.yml", tempDir), log::add);
    assertThat(error).isEmpty();

    assertThat(log).containsExactly("Exporting " + pubkey, "Exporting " + pubkey);
  }

  @Test
  public void shouldNotAcceptDifferentGenesisValidatorsRoot(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    Optional<String> error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtectionWithGenesisRoot2.yml", tempDir), LOG::debug);
    assertThat(error).isEmpty();
    error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtectionWithGenesisRoot.yml", tempDir), LOG::debug);
    assertThat(error.orElse("")).startsWith("The genesisValidatorsRoot of");
  }

  @Test
  public void shouldRequirePubkeyInFilename(@TempDir Path tempDir) throws URISyntaxException {
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    final Optional<String> error =
        exporter.readSlashProtectionFile(
            new File(Resources.getResource("slashProtectionWithGenesisRoot.yml").toURI()),
            LOG::debug);
    assertThat(error.orElse(""))
        .contains("Public key in file slashProtectionWithGenesisRoot.yml does not appear valid.");
  }

  @Test
  public void shouldPrintIfFileCannotBeRead(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    final File file = usingResourceFile("slashProtection.yml", tempDir);
    OSUtils.makeNonReadable(file.toPath());
    // It's not always possible to remove read permissions from a file
    assumeThat(file.canRead()).describedAs("Can read file %s", file).isFalse();
    final Optional<String> error = exporter.readSlashProtectionFile(file, LOG::debug);
    assertThat(error.orElse("")).startsWith("Failed to read from file");
  }

  @Test
  public void shouldExportSlashProtection(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);

    final Optional<String> error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtection.yml", tempDir), LOG::debug);
    assertThat(error).isEmpty();
    assertThat(Files.exists(exportedFile)).isFalse();
    exporter.saveToFile(exportedFile.toString(), LOG::debug);
    assertThat(Files.exists(exportedFile)).isTrue();
  }

  @Test
  void shouldHaveNoSignedAttestationsWhenNoAttestationsSigned(@TempDir Path tempDir)
      throws Exception {
    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();
    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);

    final UInt64 blockSlot = UInt64.ONE;
    final ValidatorSigningRecord signingRecord =
        new ValidatorSigningRecord(validatorsRoot)
            .maySignBlock(validatorsRoot, blockSlot)
            .orElseThrow();
    final Path recordFile = tempDir.resolve(pubkey + ".yml");
    Files.write(recordFile, signingRecord.toBytes().toArrayUnsafe());
    final Optional<String> error =
        exporter.readSlashProtectionFile(recordFile.toFile(), LOG::debug);
    assertThat(error).isEmpty();
    assertThat(exportedFile).doesNotExist();
    exporter.saveToFile(exportedFile.toString(), LOG::debug);
    assertThat(exportedFile).exists();

    final SlashingProtectionInterchangeFormat exportedRecords =
        jsonProvider.jsonToObject(
            Files.readString(exportedFile), SlashingProtectionInterchangeFormat.class);
    assertThat(exportedRecords.data).hasSize(1);
    final SigningHistory signingHistory = exportedRecords.data.get(0);
    assertThat(signingHistory.signedBlocks).containsExactly(new SignedBlock(blockSlot, null));
    assertThat(signingHistory.signedAttestations).isEmpty();
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

  private SlashingProtectionInterchangeFormat getExportData(
      final Bytes32 genesisValidatorsRoot,
      final int lastSignedBlockSlot,
      final int lastSignedAttestationSourceEpoch,
      final int lastSignedAttestationTargetEpoch) {
    return new SlashingProtectionInterchangeFormat(
        new Metadata(INTERCHANGE_VERSION, genesisValidatorsRoot),
        List.of(
            new SigningHistory(
                BLSPubKey.fromHexString(pubkey),
                new ValidatorSigningRecord(
                    genesisValidatorsRoot,
                    UInt64.valueOf(lastSignedBlockSlot),
                    UInt64.valueOf(lastSignedAttestationSourceEpoch),
                    UInt64.valueOf(lastSignedAttestationTargetEpoch)))));
  }
}
