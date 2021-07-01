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

package tech.pegasys.teku.validator.api;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

class KeyStoreFilesLocator {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<Path, Path> pathMap = new HashMap<>();
  private final List<String> colonSeparatedPairs;
  private final String pathSeparator;

  public KeyStoreFilesLocator(final List<String> colonSeparatedPairs, final String pathSeparator) {
    this.colonSeparatedPairs = colonSeparatedPairs;
    this.pathSeparator = pathSeparator;
  }

  public void parse() {
    for (final String currentEntry : colonSeparatedPairs) {
      if (!currentEntry.contains(pathSeparator)) {
        throw new InvalidConfigurationException(
            "validatorKeys entry ("
                + currentEntry
                + ") did not contain key and password separated by '"
                + pathSeparator
                + "' as expected.");
      }

      final List<String> entry = Splitter.on(pathSeparator).limit(2).splitToList(currentEntry);
      parseEntry(entry.get(0), entry.get(1));
    }
  }

  private void parseEntry(final String keyFileName, final String passwordFileName) {
    final File keyFile = new File(keyFileName);
    final File passwordFile = new File(passwordFileName);

    if (!keyFile.exists()) {
      throw new InvalidConfigurationException(
          String.format("Invalid configuration. Could not find the key file (%s).", keyFileName));
    }
    if (isDepositDataFile(keyFile)) {
      return;
    }
    if (!passwordFile.exists()) {
      throw new InvalidConfigurationException(
          String.format(
              "Invalid configuration. Could not find the password file (%s).", passwordFileName));
    }
    if (keyFile.isDirectory() != passwordFile.isDirectory()) {
      throw new InvalidConfigurationException(
          String.format(
              "Invalid configuration. --validator-keys entry (%s"
                  + pathSeparator
                  + "%s) must be both directories or both files",
              keyFileName,
              passwordFileName));
    }
    if (keyFile.isFile()) {
      pathMap.putIfAbsent(keyFile.toPath(), passwordFile.toPath());
    } else {
      parseDirectory(keyFile, passwordFile);
    }
  }

  private boolean isDepositDataFile(final File keyFile) {
    String keyFileName = keyFile.toPath().getFileName().toString();
    if (keyFileName.startsWith("deposit_data-") && hasDepositDataFileContents(keyFile)) {
      LOG.debug("Ignoring deposit data file: " + keyFileName);
      return true;
    }
    return false;
  }

  private boolean hasDepositDataFileContents(final File keyFile) {
    try {
      byte[] fileData = Files.readAllBytes(keyFile.toPath());
      ObjectMapper objectMapper = new ObjectMapper();
      List<Map<String, String>> listData =
          objectMapper.readValue(fileData, new TypeReference<>() {});

      return !listData.isEmpty()
          && listData.get(0).containsKey("deposit_message_root")
          && listData.get(0).containsKey("deposit_data_root");
    } catch (final Exception e) {
      LOG.debug("Unable to determine if {} is a deposit_data file", keyFile, e);
      return false;
    }
  }

  void parseDirectory(final File keyDirectory, final File passwordDirectory) {
    try (Stream<Path> walk = Files.walk(keyDirectory.toPath(), FileVisitOption.FOLLOW_LINKS)) {
      walk.filter(Files::isRegularFile)
          .filter(
              (path) ->
                  !path.toFile().isHidden() && path.getFileName().toString().endsWith(".json"))
          .forEach(
              path -> {
                final Path relativeDirectoryPath =
                    keyDirectory.toPath().relativize(path.getParent());
                if (isDepositDataFile(path.toFile())) {
                  return;
                }
                final String keystoreName = path.getFileName().toString();
                final File passwordFile =
                    passwordDirectory
                        .toPath()
                        .resolve(relativeDirectoryPath)
                        .resolve(
                            keystoreName.substring(0, keystoreName.length() - ".json".length())
                                + ".txt")
                        .toFile();
                if (!passwordFile.isFile()) {
                  throw new InvalidConfigurationException(
                      String.format(
                          "Invalid configuration. Password file for keystore %s either doesn't exist or isn't readable. Expected to find it at %s",
                          path.toAbsolutePath(), passwordFile.getAbsolutePath()));
                }
                pathMap.putIfAbsent(path, passwordFile.toPath());
              });
    } catch (IOException e) {
      LOG.fatal("Failed to load keys from keystore", e);
    }
  }

  public List<Pair<Path, Path>> getFilePairs() {
    return pathMap.entrySet().stream()
        .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
        .collect(toList());
  }
}
