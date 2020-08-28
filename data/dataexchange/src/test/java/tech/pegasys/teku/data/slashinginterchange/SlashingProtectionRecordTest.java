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

package tech.pegasys.teku.data.slashinginterchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtectionRecordTest {
  private final YamlProvider yamlProvider = new YamlProvider();
  final ObjectMapper yamlMapper = yamlProvider.getObjectMapper();
  private final Bytes32 genesisValidatorsRoot =
      Bytes32.fromHexString("0x6e2c5d8a89dfe121a92c8812bea69fe9f84ae48f63aafe34ef7e18c7eac9af70");
  private final SlashingProtectionRecord sampleDataWithGenesisRoot =
      new SlashingProtectionRecord(
          UInt64.valueOf(327), UInt64.valueOf(51), UInt64.valueOf(1741), genesisValidatorsRoot);
  private final SlashingProtectionRecord sampleData =
      new SlashingProtectionRecord(
          UInt64.valueOf(327), UInt64.valueOf(51), UInt64.valueOf(1741), null);

  @Test
  public void shouldReadFromSlashProtectionWithoutGenesisRoot() throws IOException {
    final String yamlData =
        Resources.toString(Resources.getResource("slashProtection.yml"), StandardCharsets.UTF_8);
    JsonNode jsonNode = yamlMapper.readTree(yamlData);
    SlashingProtectionRecord slashingProtectionRecord =
        yamlMapper.treeToValue(jsonNode, SlashingProtectionRecord.class);
    assertThat(slashingProtectionRecord).isEqualTo(sampleData);
  }

  @Test
  public void shouldSerialise(@TempDir final Path tempDir) throws IOException {
    final File testFile = Path.of(tempDir.toString(), "test.yml").toFile();
    final File referenceFile =
        new File(Resources.getResource("slashProtectionWithGenesisRoot.yml").getPath());
    yamlMapper.writeValue(testFile, sampleDataWithGenesisRoot);
    assertThat(yamlProvider.fileToObject(testFile, SlashingProtectionRecord.class))
        .isEqualTo(yamlProvider.fileToObject(referenceFile, SlashingProtectionRecord.class));
  }

  @Test
  public void shouldReadSlashProtectionWithGenesisRoot() throws IOException {
    final String yamlData =
        Resources.toString(
            Resources.getResource("slashProtectionWithGenesisRoot.yml"), StandardCharsets.UTF_8);
    JsonNode jsonNode = yamlMapper.readTree(yamlData);
    SlashingProtectionRecord slashingProtectionRecord =
        yamlMapper.treeToValue(jsonNode, SlashingProtectionRecord.class);
    assertThat(slashingProtectionRecord).isEqualTo(sampleDataWithGenesisRoot);
  }
}
