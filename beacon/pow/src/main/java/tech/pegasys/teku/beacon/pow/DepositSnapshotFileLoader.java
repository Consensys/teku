/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.beacon.pow;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.schema.LoadDepositSnapshotResult;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class DepositSnapshotFileLoader {

  public static final Map<Eth2Network, String> DEFAULT_SNAPSHOT_RESOURCE_PATHS =
      Map.of(
          Eth2Network.GNOSIS, "gnosis.ssz",
          Eth2Network.PRATER, "goerli.ssz",
          Eth2Network.MAINNET, "mainnet.ssz",
          Eth2Network.SEPOLIA, "sepolia.ssz",
          Eth2Network.LUKSO, "lukso.ssz",
          Eth2Network.HOLESKY, "holesky.ssz");

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<String> depositSnapshotResource;

  public DepositSnapshotFileLoader(final Optional<String> depositSnapshotResource) {
    this.depositSnapshotResource = depositSnapshotResource;
  }

  public LoadDepositSnapshotResult loadDepositSnapshot() {
    final Optional<DepositTreeSnapshot> depositTreeSnapshot =
        loadDepositSnapshot(depositSnapshotResource);
    return LoadDepositSnapshotResult.create(depositTreeSnapshot);
  }

  private Optional<DepositTreeSnapshot> loadDepositSnapshot(
      final Optional<String> depositSnapshotResource) {
    return depositSnapshotResource.map(
        snapshotPath -> {
          final String sanitizedResource = UrlSanitizer.sanitizePotentialUrl(snapshotPath);
          try {
            STATUS_LOG.loadingDepositSnapshotResource(sanitizedResource);
            final DepositTreeSnapshot depositSnapshot = loadFromUrl(snapshotPath);
            STATUS_LOG.onDepositSnapshot(
                depositSnapshot.getDepositCount(), depositSnapshot.getExecutionBlockHash());
            return depositSnapshot;
          } catch (IOException e) {
            LOG.error("Failed to load deposit tree snapshot", e);
            throw new InvalidConfigurationException(
                "Failed to load deposit tree snapshot from "
                    + sanitizedResource
                    + ": "
                    + e.getMessage());
          }
        });
  }

  private DepositTreeSnapshot loadFromUrl(final String path) throws IOException {
    final Bytes snapshotData =
        ResourceLoader.urlOrFile("application/octet-stream, application/json")
            .loadBytes(path)
            .orElseThrow(
                () -> new FileNotFoundException(String.format("File '%s' not found", path)));

    try {
      return parseSszDepositSnapshotTreeData(snapshotData);
    } catch (final SszDeserializeException e) {
      return parseJsonDepositSnapshotTreeData(snapshotData);
    }
  }

  private static DepositTreeSnapshot parseSszDepositSnapshotTreeData(final Bytes snapshotData) {
    return DepositTreeSnapshot.fromBytes(snapshotData);
  }

  private static DepositTreeSnapshot parseJsonDepositSnapshotTreeData(final Bytes snapshotData)
      throws JsonProcessingException {
    final String json = new String(snapshotData.toArray(), StandardCharsets.UTF_8);
    final JsonNode jsonNode = new ObjectMapper().readTree(json);
    return JsonUtil.parse(
        jsonNode.get("data").toString(), DepositTreeSnapshot.getJsonTypeDefinition());
  }
}
