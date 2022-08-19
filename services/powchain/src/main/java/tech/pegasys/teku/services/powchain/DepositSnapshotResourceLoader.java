/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.services.powchain;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;

public class DepositSnapshotResourceLoader {
  private static final Logger LOG = LogManager.getLogger();

  public Optional<DepositTreeSnapshot> loadDepositSnapshot(
      final Optional<String> depositSnapshotResource) {
    return depositSnapshotResource.map(
        snapshotResource -> {
          final String sanitizedResource = UrlSanitizer.sanitizePotentialUrl(snapshotResource);
          try {
            STATUS_LOG.loadingDepositSnapshot(sanitizedResource);
            final DepositTreeSnapshot depositSnapshot = loadFromResource(snapshotResource);
            STATUS_LOG.loadedDepositSnapshotResource(
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

  private DepositTreeSnapshot loadFromResource(final String source) throws IOException {
    return DepositTreeSnapshot.fromBytes(
        ResourceLoader.urlOrFile("application/octet-stream")
            .loadBytes(source)
            .orElseThrow(() -> new FileNotFoundException("Not found")));
  }
}
