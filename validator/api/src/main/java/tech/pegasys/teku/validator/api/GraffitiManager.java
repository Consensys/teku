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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    return updateGraffiti(publicKey, () -> Bytes32Parser.toBytes32(graffiti).toArray());
  }

  public Optional<String> deleteGraffiti(final BLSPublicKey publicKey) {
    return updateGraffiti(publicKey, () -> new byte[0]);
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

  private Optional<String> updateGraffiti(
      final BLSPublicKey publicKey, final Supplier<byte[]> graffiti) {
    if (graffitiPath.isEmpty()) {
      return Optional.of(GRAFFITI_DIR + " directory does not exist to handle update.");
    }

    try {
      final Path file = graffitiPath.get().resolve(resolveFileName(publicKey));
      Files.write(file, graffiti.get());
    } catch (IOException | IllegalArgumentException e) {
      return Optional.of(e.toString());
    }
    return Optional.empty();
  }

  static String resolveFileName(final BLSPublicKey publicKey) {
    return publicKey.toSSZBytes().toUnprefixedHexString() + ".txt";
  }
}
