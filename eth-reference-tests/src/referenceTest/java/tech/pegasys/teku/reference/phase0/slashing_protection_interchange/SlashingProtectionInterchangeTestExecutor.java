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

package tech.pegasys.teku.reference.phase0.slashing_protection_interchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.signatures.LocalSlashingProtector;

public class SlashingProtectionInterchangeTestExecutor implements TestExecutor {

  private static final Logger LOG = LogManager.getLogger();

  //TODO: implement the logic
  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final JsonNode testNode = TestDataUtils.loadJson(testDefinition, testDefinition.getTestName());

    final String testName = testNode.get("name").asText();
    final Bytes32 genesisValidatorsRoot =
        Bytes32.fromHexString(testNode.get("genesis_validators_root").asText());

    LOG.info("Running {}", testName);

    final Path tempDir = Files.createTempDirectory(testName);

    final Path slashProtectionPath = tempDir.resolve("slashprotection");

    final Path slashProtectionImportFile = tempDir.resolve("import.yml");

    Files.writeString(
        slashProtectionImportFile,
        testNode.get("steps").get(0).get("interchange").toString(),
        StandardCharsets.UTF_8);

    final BLSPublicKey pubkey =
        BLSPublicKey.fromHexString(
            "0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c");

    Files.createDirectories(slashProtectionPath);
    SlashingProtectionImporter importer = new SlashingProtectionImporter(slashProtectionPath);
    importer.initialise(slashProtectionImportFile.toFile());
    final Map<BLSPublicKey, String> errors = importer.updateLocalRecords((__) -> {});

    assertThat(errors).isEmpty();

    LocalSlashingProtector localSlashingProtector =
        new LocalSlashingProtector(
            SyncDataAccessor.create(slashProtectionPath), slashProtectionPath);
    assertThat(
            localSlashingProtector.maySignBlock(pubkey, genesisValidatorsRoot, UInt64.valueOf(10)))
        .isCompletedWithValue(false);
    assertThat(
            localSlashingProtector.maySignBlock(pubkey, genesisValidatorsRoot, UInt64.valueOf(13)))
        .isCompletedWithValue(false);
    assertThat(
            localSlashingProtector.maySignBlock(pubkey, genesisValidatorsRoot, UInt64.valueOf(14)))
        .isCompletedWithValue(true);
    assertThat(
            localSlashingProtector.maySignAttestation(
                pubkey, genesisValidatorsRoot, UInt64.valueOf(0), UInt64.valueOf(2)))
        .isCompletedWithValue(false);
    assertThat(
            localSlashingProtector.maySignAttestation(
                pubkey, genesisValidatorsRoot, UInt64.valueOf(1), UInt64.valueOf(3)))
        .isCompletedWithValue(false);
  }
}
