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

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.PublicKeyException;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionInterchangeFormat;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.provider.JsonProvider;

public class SlashingProtectionExporter {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final List<SigningHistory> signingHistoryList = new ArrayList<>();
  private Bytes32 genesisValidatorsRoot = null;
  private final SyncDataAccessor syncDataAccessor = new SyncDataAccessor();
  private final SubCommandLogger log;

  public SlashingProtectionExporter(final SubCommandLogger log) {
    this.log = log;
  }

  public void initialise(final Path slashProtectionPath) {
    File slashingProtectionRecords = slashProtectionPath.toFile();
    Arrays.stream(slashingProtectionRecords.listFiles())
        .filter(file -> file.isFile() && file.getName().endsWith(".yml"))
        .forEach(this::readSlashProtectionFile);
  }

  private void readSlashProtectionFile(final File file) {
    try {
      Optional<ValidatorSigningRecord> maybeRecord =
          syncDataAccessor.read(file.toPath()).map(ValidatorSigningRecord::fromBytes);
      if (maybeRecord.isEmpty()) {
        log.exit(1, "Failed to read from file " + file.getName());
      }
      ValidatorSigningRecord validatorSigningRecord = maybeRecord.get();

      if (genesisValidatorsRoot == null
          && validatorSigningRecord.getGenesisValidatorsRoot() != null) {
        this.genesisValidatorsRoot = validatorSigningRecord.getGenesisValidatorsRoot();
      } else if (validatorSigningRecord.getGenesisValidatorsRoot() != null
          && !genesisValidatorsRoot.equals(validatorSigningRecord.getGenesisValidatorsRoot())) {
        log.exit(
            1,
            "The genesisValidatorsRoot of "
                + file.getName()
                + " does not match the expected "
                + genesisValidatorsRoot.toHexString());
      }
      final String pubkey = file.getName().substring(0, file.getName().length() - ".yml".length());
      log.display("Exporting " + pubkey);
      signingHistoryList.add(
          new SigningHistory(BLSPubKey.fromHexString(pubkey), validatorSigningRecord));
    } catch (IOException e) {
      log.exit(1, "Failed to read from file " + file.toString(), e);
    } catch (PublicKeyException e) {
      log.exit(1, "Public key in file " + file.getName() + " does not appear valid.");
    }
  }

  public void saveToFile(final String toFileName) throws IOException {
    syncDataAccessor.syncedWrite(Path.of(toFileName), getJsonByteData());
    log.display(
        "Wrote "
            + signingHistoryList.size()
            + " validator slashing protection records to "
            + toFileName);
  }

  private Bytes getJsonByteData() throws JsonProcessingException {
    final String prettyJson =
        jsonProvider.objectToPrettyJSON(
            new SlashingProtectionInterchangeFormat(
                new Metadata(INTERCHANGE_VERSION, genesisValidatorsRoot), signingHistoryList));
    return Bytes.of(prettyJson.getBytes(StandardCharsets.UTF_8));
  }
}
