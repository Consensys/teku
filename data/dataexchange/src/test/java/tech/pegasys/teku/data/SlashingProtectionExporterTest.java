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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.cli.OSUtils;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.SignedBlock;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionInterchangeFormat;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class SlashingProtectionExporterTest {
  private final ArgumentCaptor<String> stringArgs = ArgumentCaptor.forClass(String.class);
  final SubCommandLogger logger = mock(SubCommandLogger.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final String pubkey =
      "b845089a1457f811bfc000588fbb4e713669be8ce060ea6be3c6ece09afc3794106c91ca73acda5e5457122d58723bed";
  private final Bytes32 validatorsRoot =
      Bytes32.fromHexString("0x6e2c5d8a89dfe121a92c8812bea69fe9f84ae48f63aafe34ef7e18c7eac9af70");

  @Test
  public void shouldReadSlashingProtectionFile_withEmptyGenesisValidatorsRoot(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    exporter.readSlashProtectionFile(usingResourceFile("slashProtection.yml", tempDir));
    verify(logger, times(1)).display("Exporting " + pubkey);

    final SlashingProtectionInterchangeFormat parsedData =
        jsonProvider.jsonToObject(
            exporter.getPrettyJson(), SlashingProtectionInterchangeFormat.class);
    final SlashingProtectionInterchangeFormat expectedData = getExportData(null, 327, 51, 1741);
    assertThat(parsedData).isEqualTo(expectedData);

    verify(logger, never()).exit(eq(1), stringArgs.capture());
  }

  @Test
  public void shouldReadSlashingProtectionFile_withGenesisValidatorsRoot(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    exporter.readSlashProtectionFile(
        usingResourceFile("slashProtectionWithGenesisRoot.yml", tempDir));
    verify(logger, times(1)).display("Exporting " + pubkey);

    final SlashingProtectionInterchangeFormat parsedData =
        jsonProvider.jsonToObject(
            exporter.getPrettyJson(), SlashingProtectionInterchangeFormat.class);
    final SlashingProtectionInterchangeFormat expectedData =
        getExportData(validatorsRoot, 327, 51, 1741);
    assertThat(parsedData).isEqualTo(expectedData);

    verify(logger, never()).exit(eq(1), stringArgs.capture());
  }

  @Test
  public void shouldReadFilesWithEmptyRootAfterGenesisRootIsDefined(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    exporter.readSlashProtectionFile(
        usingResourceFile("slashProtectionWithGenesisRoot.yml", tempDir));
    exporter.readSlashProtectionFile(usingResourceFile("slashProtection.yml", tempDir));

    verify(logger, never()).exit(eq(1), stringArgs.capture());
    verify(logger, times(2)).display("Exporting " + pubkey);
  }

  @Test
  public void shouldReadFileWithGenesisRootDefinedSecond(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    exporter.readSlashProtectionFile(usingResourceFile("slashProtection.yml", tempDir));
    exporter.readSlashProtectionFile(
        usingResourceFile("slashProtectionWithGenesisRoot.yml", tempDir));

    verify(logger, never()).exit(eq(1), stringArgs.capture());
    verify(logger, times(2)).display("Exporting " + pubkey);
  }

  @Test
  public void shouldNotAcceptDifferentGenesisValidatorsRoot(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    exporter.readSlashProtectionFile(
        usingResourceFile("slashProtectionWithGenesisRoot2.yml", tempDir));
    exporter.readSlashProtectionFile(
        usingResourceFile("slashProtectionWithGenesisRoot.yml", tempDir));

    verify(logger).exit(eq(1), stringArgs.capture());
    assertThat(stringArgs.getValue()).startsWith("The genesisValidatorsRoot of");
  }

  @Test
  public void shouldRequirePubkeyInFilename(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    exporter.readSlashProtectionFile(
        new File(Resources.getResource("slashProtectionWithGenesisRoot.yml").toURI()));

    verify(logger).exit(eq(1), stringArgs.capture());
    assertThat(stringArgs.getValue())
        .contains("Public key in file slashProtectionWithGenesisRoot.yml does not appear valid.");
  }

  @Test
  public void shouldPrintIfFileCannotBeRead(@TempDir Path tempDir)
      throws URISyntaxException, IOException {
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());
    final File file = usingResourceFile("slashProtection.yml", tempDir);
    OSUtils.makeNonReadable(file.toPath());
    // It's not always possible to remove read permissions from a file
    assumeThat(file.canRead()).describedAs("Can read file %s", file).isFalse();
    exporter.readSlashProtectionFile(file);
    verify(logger).exit(eq(1), stringArgs.capture(), any(AccessDeniedException.class));
    assertThat(stringArgs.getValue()).startsWith("Failed to read from file");
  }

  @Test
  public void shouldExportSlashProtection(@TempDir Path tempDir)
      throws IOException, URISyntaxException {
    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());

    exporter.readSlashProtectionFile(usingResourceFile("slashProtection.yml", tempDir));

    assertThat(Files.exists(exportedFile)).isFalse();
    exporter.saveToFile(exportedFile.toString());
    assertThat(Files.exists(exportedFile)).isTrue();
  }

  @Test
  void shouldHaveNoSignedAttestationsWhenNoAttestationsSigned(@TempDir Path tempDir)
      throws Exception {
    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();
    final SlashingProtectionExporter exporter =
        new SlashingProtectionExporter(logger, tempDir.toString());

    final UInt64 blockSlot = UInt64.ONE;
    final ValidatorSigningRecord signingRecord =
        new ValidatorSigningRecord(validatorsRoot)
            .maySignBlock(validatorsRoot, blockSlot)
            .orElseThrow();
    final Path recordFile = tempDir.resolve(pubkey + ".yml");
    Files.write(recordFile, signingRecord.toBytes().toArrayUnsafe());
    exporter.readSlashProtectionFile(recordFile.toFile());

    assertThat(exportedFile).doesNotExist();
    exporter.saveToFile(exportedFile.toString());
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
