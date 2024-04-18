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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;

public class GraffitiManager {
  static final String GRAFFITI_DIR = "graffiti";

  private static final Logger LOG = LogManager.getLogger();
  private final Optional<Path> graffitiPath;

  public GraffitiManager(final DataDirLayout dataDirLayout) {
    this.graffitiPath = createManagementDirectory(dataDirLayout);
  }

  public Optional<String> setGraffiti(final BLSPublicKey publicKey, final String graffiti) {
    return updateGraffiti(publicKey, graffiti.strip());
  }

  public Optional<String> deleteGraffiti(final BLSPublicKey publicKey) {
    return updateGraffiti(publicKey);
  }

  private Optional<Path> createManagementDirectory(final DataDirLayout dataDirLayout) {
    final Path graffitiDirectory = dataDirLayout.getValidatorDataDirectory().resolve(GRAFFITI_DIR);
    if (!graffitiDirectory.toFile().exists() && !graffitiDirectory.toFile().mkdirs()) {
      LOG.error(
          "Unable to create {} directory. Updating graffiti through the validator API is disabled.",
          GRAFFITI_DIR);
      return Optional.empty();
    }
    return Optional.of(graffitiDirectory);
  }

  private Optional<String> updateGraffiti(final BLSPublicKey publicKey) {
    return updateGraffiti(publicKey, "");
  }

  private Optional<String> updateGraffiti(final BLSPublicKey publicKey, final String graffiti) {
    if (graffitiPath.isEmpty()) {
      return Optional.of(GRAFFITI_DIR + " directory does not exist to handle update.");
    }

    final int graffitiSize = graffiti.getBytes(StandardCharsets.UTF_8).length;
    if (graffitiSize > 32) {
      return Optional.of(
          String.format(
              "'%s' converts to %s bytes. Input must be 32 bytes or less.",
              graffiti, graffitiSize));
    }

    try {
      final Path file = graffitiPath.get().resolve(resolveFileName(publicKey));
      Files.writeString(file, graffiti);
    } catch (IOException e) {
      final String errorMessage =
          String.format("Unable to update graffiti for validator %s", publicKey);
      LOG.error(errorMessage, e);
      return Optional.of(errorMessage);
    }
    return Optional.empty();
  }

  public Optional<Bytes32> getGraffitiFromStorage(final BLSPublicKey publicKey) {
    if (graffitiPath.isEmpty()) {
      LOG.error(GRAFFITI_DIR + " directory does not exist to get graffiti from storage.");
      return Optional.empty();
    }

    final Path filePath = graffitiPath.get().resolve(resolveFileName(publicKey));
    try {
      return Optional.of(GraffitiParser.loadFromFile(filePath)).filter(this::graffitiNotEmpty);
    } catch (GraffitiLoaderException | IllegalArgumentException e) {
      LOG.error("Unable to read graffiti from storage.", e);
      return Optional.empty();
    }
  }

  private boolean graffitiNotEmpty(final Bytes32 graffiti) {
    final Bytes32 emptyBytesParsed = Bytes32Parser.toBytes32(new byte[0]);
    return !graffiti.equals(emptyBytesParsed);
  }

  private String resolveFileName(final BLSPublicKey publicKey) {
    return publicKey.toSSZBytes().toUnprefixedHexString() + ".txt";
  }
}
