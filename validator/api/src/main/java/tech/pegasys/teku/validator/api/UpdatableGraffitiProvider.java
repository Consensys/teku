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

package tech.pegasys.teku.validator.api;

import static tech.pegasys.teku.validator.api.GraffitiManager.GRAFFITI_DIR;

import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;

public class UpdatableGraffitiProvider implements GraffitiProvider {
  private static final Logger LOG = LogManager.getLogger();

  private final Path graffitiPath;
  private final GraffitiProvider defaultProvider;

  public UpdatableGraffitiProvider(
      final DataDirLayout dataDirLayout,
      final BLSPublicKey publicKey,
      final GraffitiProvider defaultProvider) {
    this.graffitiPath =
        dataDirLayout
            .getValidatorDataDirectory()
            .resolve(GRAFFITI_DIR)
            .resolve(GraffitiManager.resolveFileName(publicKey));
    this.defaultProvider = defaultProvider;
  }

  @Override
  public Optional<Bytes32> get() {
    return getGraffitiFromStorage().or(defaultProvider::get);
  }

  private Optional<Bytes32> getGraffitiFromStorage() {
    if (!graffitiPath.toFile().exists()) {
      return Optional.empty();
    }

    try {
      return Optional.of(GraffitiParser.loadFromFile(graffitiPath)).filter(this::graffitiNotEmpty);
    } catch (GraffitiLoaderException | IllegalArgumentException e) {
      LOG.warn("Unable to read graffiti from storage", e);
      return Optional.empty();
    }
  }

  private boolean graffitiNotEmpty(final Bytes32 graffiti) {
    final Bytes32 emptyBytesParsed = Bytes32Parser.toBytes32(new byte[0]);
    return !graffiti.equals(emptyBytesParsed);
  }
}
