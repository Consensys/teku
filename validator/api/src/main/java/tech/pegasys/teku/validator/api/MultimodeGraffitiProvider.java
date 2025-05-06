/*
 * Copyright Consensys Software Inc., 2025
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;

/**
 * A GraffitiProvider that supports multiple graffiti entries in various formats: - specific: Allows
 * setting specific graffiti for individual validators by index or public key - ordered: Use
 * graffiti messages in the specified order, cycling through them - random: Randomly select graffiti
 * from a list - default: Fallback graffiti when no other option applies
 */
public class MultimodeGraffitiProvider implements GraffitiProvider {
  private static final Logger LOG = LogManager.getLogger();
  private static final Random RANDOM = new Random();

  private final Optional<Bytes32> defaultGraffiti;
  private final Optional<BLSPublicKey> validatorPublicKey;
  private final Optional<Path> graffitiFile;
  private final AtomicInteger orderedIndex = new AtomicInteger(0);

  private volatile Optional<GraffitiConfiguration> graffitiConfig = Optional.empty();
  private volatile long lastFileCheckTime = 0;
  private volatile long lastFileModifiedTime = 0;

  public MultimodeGraffitiProvider(
      final Optional<Bytes32> defaultGraffiti,
      final Optional<BLSPublicKey> validatorPublicKey,
      final Optional<Path> graffitiFile) {
    this.defaultGraffiti = defaultGraffiti;
    this.validatorPublicKey = validatorPublicKey;
    this.graffitiFile = graffitiFile;
  }

  @Override
  public Optional<Bytes32> get() {
    // Check if we need to reload the file
    checkAndReloadFileIfNeeded();

    // If we have a configuration from file, try to get graffiti from it
    if (graffitiConfig.isPresent()) {
      final GraffitiConfiguration config = graffitiConfig.get();

      // Priority 1: Check for specific validator entry if we have a public key
      if (validatorPublicKey.isPresent()) {
        final BLSPublicKey pubKey = validatorPublicKey.get();

        // Try to find by full public key
        final String pubKeyStr = pubKey.toSSZBytes().toUnprefixedHexString();
        final Optional<Bytes32> specificGraffiti = findSpecificGraffiti(config, pubKeyStr);
        if (specificGraffiti.isPresent()) {
          return specificGraffiti;
        }
      }

      // Priority 2: Try ordered list if available
      if (config.ordered != null && !config.ordered.isEmpty()) {
        final int index = orderedIndex.getAndIncrement() % config.ordered.size();
        final String orderedGraffiti = config.ordered.get(index);
        return Optional.of(Bytes32Parser.toBytes32(orderedGraffiti));
      }

      // Priority 3: Try random selection if available
      if (config.random != null && !config.random.isEmpty()) {
        final int randomIndex = RANDOM.nextInt(config.random.size());
        final String randomGraffiti = config.random.get(randomIndex);
        return Optional.of(Bytes32Parser.toBytes32(randomGraffiti));
      }

      // Priority 4: Use default from config file
      if (config.defaultGraffiti != null) {
        return Optional.of(Bytes32Parser.toBytes32(config.defaultGraffiti));
      }
    }

    // Last resort: Fall back to CLI provided default
    return defaultGraffiti;
  }

  private Optional<Bytes32> findSpecificGraffiti(
      final GraffitiConfiguration config, final String pubKeyStr) {
    if (config.specific == null) {
      return Optional.empty();
    }

    // Try to match by validator public key
    if (config.specific.containsKey(pubKeyStr)) {
      final String specificGraffiti = config.specific.get(pubKeyStr);
      return Optional.of(Bytes32Parser.toBytes32(specificGraffiti));
    }

    // No match found
    return Optional.empty();
  }

  private void checkAndReloadFileIfNeeded() {
    if (graffitiFile.isEmpty()) {
      return;
    }

    final Path path = graffitiFile.get();
    final long now = System.currentTimeMillis();

    // Only check file modification time every few seconds to avoid excessive file operations
    if (now - lastFileCheckTime > 5000) {
      lastFileCheckTime = now;

      try {
        if (Files.exists(path)) {
          final long fileModifiedTime = Files.getLastModifiedTime(path).toMillis();
          if (fileModifiedTime != lastFileModifiedTime) {
            lastFileModifiedTime = fileModifiedTime;
            loadGraffitiFromFile(path);
          }
        } else {
          // If file doesn't exist, clear config
          graffitiConfig = Optional.empty();
        }
      } catch (IOException e) {
        LOG.error("Failed to check graffiti file modified time: {}", path, e);
      }
    }
  }

  private void loadGraffitiFromFile(final Path path) {
    try {
      final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      final GraffitiConfiguration config =
          mapper.readValue(path.toFile(), GraffitiConfiguration.class);
      graffitiConfig = Optional.of(config);
      LOG.debug("Loaded graffiti configuration from file: {}", path);
    } catch (IOException e) {
      LOG.error("Failed to load graffiti from file {}: {}", path, e.getMessage());
      graffitiConfig = Optional.empty();
    }
  }

  // Configuration class to match file structure
  public static class GraffitiConfiguration {
    public Map<String, String> specific;
    public List<String> ordered;
    public List<String> random;
    public String defaultGraffiti;
  }
}
