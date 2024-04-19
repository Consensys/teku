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
import java.nio.file.NoSuchFileException;
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
  private final Path graffitiPath;

  public GraffitiManager(final DataDirLayout dataDirLayout) {
    this.graffitiPath = createManagementDirectory(dataDirLayout);
  }

  private Path createManagementDirectory(final DataDirLayout dataDirLayout) {
    final Path graffitiDirectory = dataDirLayout.getValidatorDataDirectory().resolve(GRAFFITI_DIR);
    if (!graffitiDirectory.toFile().exists() && !graffitiDirectory.toFile().mkdirs()) {
      throw new IllegalStateException(
          "Unable to create " + GRAFFITI_DIR + " directory for graffiti management.");
    }
    return graffitiDirectory;
  }

  public Optional<String> setGraffiti(final BLSPublicKey publicKey, final String graffiti) {
    final String strippedGraffiti = graffiti.strip();
    final int graffitiSize = strippedGraffiti.getBytes(StandardCharsets.UTF_8).length;
    if (graffitiSize > 32) {
      throw new IllegalArgumentException(
          String.format(
              "'%s' converts to %s bytes. Input must be 32 bytes or less.",
              strippedGraffiti, graffitiSize));
    }

    try {
      final Path file = graffitiPath.resolve(resolveFileName(publicKey));
      Files.writeString(file, strippedGraffiti);
    } catch (IOException e) {
      final String errorMessage =
          String.format("Unable to update graffiti for validator %s", publicKey);
      LOG.error(errorMessage, e);
      return Optional.of(errorMessage);
    }
    return Optional.empty();
  }

  public Optional<String> deleteGraffiti(final BLSPublicKey publicKey) {
    final Path file = graffitiPath.resolve(resolveFileName(publicKey));

    try {
      Files.delete(file);
      return Optional.empty();
    } catch (NoSuchFileException e) {
      throw new IllegalArgumentException(
          "Saved graffiti does not exist for validator " + publicKey);
    } catch (IOException e) {
      final String errorMessage =
          String.format("Unable to delete graffiti for validator %s", publicKey);
      LOG.error(errorMessage, e);
      return Optional.of(errorMessage);
    }
  }

  public Optional<Bytes32> getGraffiti(final BLSPublicKey publicKey) {
    final Path filePath = graffitiPath.resolve(resolveFileName(publicKey));
    if (!filePath.toFile().exists()) {
      return Optional.empty();
    }

    try {
      return Optional.of(GraffitiParser.loadFromFile(filePath));
    } catch (GraffitiLoaderException | IllegalArgumentException e) {
      LOG.error("Unable to read graffiti from storage.", e);
      return Optional.empty();
    }
  }

  private String resolveFileName(final BLSPublicKey publicKey) {
    return publicKey.toSSZBytes().toUnprefixedHexString() + ".txt";
  }
}
