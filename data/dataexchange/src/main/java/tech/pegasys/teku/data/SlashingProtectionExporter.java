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

import static tech.pegasys.teku.logging.SubCommandLogger.SUB_COMMAND_LOG;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.PublicKeyException;
import tech.pegasys.teku.data.slashinginterchange.InterchangeFormat;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.MinimalSigningHistory;
import tech.pegasys.teku.data.slashinginterchange.MinimalSlashingProtectionInterchangeFormat;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionRecord;
import tech.pegasys.teku.data.slashinginterchange.YamlProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class SlashingProtectionExporter {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final YamlProvider yamlProvider = new YamlProvider();
  private final List<MinimalSigningHistory> minimalSigningHistoryList = new ArrayList<>();
  private Bytes32 genesisValidatorsRoot = null;

  public void initialise(final Path slashProtectionPath) {
    File slashingProtectionRecords = slashProtectionPath.toFile();
    Arrays.stream(slashingProtectionRecords.listFiles())
        .filter(file -> file.isFile() && file.getName().endsWith(".yml"))
        .forEach(this::readSlashProtectionFile);
  }

  private void readSlashProtectionFile(final File file) {
    try {
      SlashingProtectionRecord slashingProtectionRecord =
          yamlProvider.fileToObject(file, SlashingProtectionRecord.class);
      if (genesisValidatorsRoot == null && slashingProtectionRecord.genesisValidatorsRoot != null) {
        this.genesisValidatorsRoot = slashingProtectionRecord.genesisValidatorsRoot;
      } else if (slashingProtectionRecord.genesisValidatorsRoot != null
          && !genesisValidatorsRoot.equals(slashingProtectionRecord.genesisValidatorsRoot)) {
        SUB_COMMAND_LOG.error(
            "the genesisValidatorsRoot of "
                + file.getName()
                + " does not match the expected "
                + genesisValidatorsRoot.toHexString().toLowerCase());
        System.exit(1);
      }
      final String pubkey = file.getName().substring(0, file.getName().length() - ".yml".length());
      minimalSigningHistoryList.add(
          new MinimalSigningHistory(BLSPubKey.fromHexString(pubkey), slashingProtectionRecord));
    } catch (IOException e) {
      SUB_COMMAND_LOG.error("Failed to read from file " + file.toString());
      System.exit(1);
    } catch (PublicKeyException e) {
      SUB_COMMAND_LOG.error("Public key in file " + file.getName() + " does not appear valid.");
      System.exit(1);
    }
  }

  public void saveToFile(final String toFileName) throws IOException {
    Files.writeString(
        Path.of(toFileName),
        jsonProvider.objectToPrettyJSON(
            new MinimalSlashingProtectionInterchangeFormat(
                new Metadata(InterchangeFormat.minimal, UInt64.valueOf(2L), genesisValidatorsRoot),
                minimalSigningHistoryList)));
  }
}
