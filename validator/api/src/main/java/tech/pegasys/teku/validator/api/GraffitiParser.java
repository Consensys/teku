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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes32;

public class GraffitiParser {
  public static Bytes32 loadFromFile(final Path graffitiFile) throws GraffitiLoaderException {
    try {
      checkNotNull(graffitiFile, "GraffitiFile path cannot be null");
      if (Files.size(graffitiFile) > 32) {
        throw new GraffitiLoaderException("GraffitiFile size too big, maximum size is 32 bytes");
      }
      return Bytes32Parser.toBytes32(strip(Files.newInputStream(graffitiFile).readNBytes(32)));
    } catch (final FileNotFoundException e) {
      throw new GraffitiLoaderException("GraffitiFile file not found: " + graffitiFile, e);
    } catch (final IOException e) {
      throw new GraffitiLoaderException(
          "Unexpected IO error while reading GraffitiFile: " + e.getMessage(), e);
    }
  }

  private static byte[] strip(final byte[] value) {
    return new String(value, StandardCharsets.UTF_8).strip().getBytes(StandardCharsets.UTF_8);
  }
}
