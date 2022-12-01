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

package tech.pegasys.teku.kzg.ckzg4844;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;

public class CKZG4844Utils {

  private static final String FILE_SCHEME = "file";

  public static byte[] flattenBytesListToArray(final List<Bytes> bytes) {
    return flattenBytesStreamToArray(bytes.stream());
  }

  public static byte[] flattenBytesStreamToArray(final Stream<Bytes> bytes) {
    return bytes.reduce(Bytes::wrap).orElse(Bytes.EMPTY).toArray();
  }

  public static String copyTrustedSetupToTempFileIfNeeded(final URL trustedSetup)
      throws IOException {
    try {
      if (trustedSetup.toURI().getScheme().equals(FILE_SCHEME)) {
        // Platform-agnostic safe way to get path
        return Paths.get(trustedSetup.toURI()).toFile().getPath();
      }
    } catch (final URISyntaxException ex) {
      throw new IllegalArgumentException(
          String.format("%s is incorrect file path", trustedSetup), ex);
    }

    final InputStream resource =
        ResourceLoader.urlOrFile("application/octet-stream")
            .load(trustedSetup.toExternalForm())
            .orElseThrow(() -> new FileNotFoundException(trustedSetup + " is not found"));

    final Path temp = Files.createTempFile("trusted_setup", ".tmp");
    temp.toFile().deleteOnExit();
    Files.copy(resource, temp, StandardCopyOption.REPLACE_EXISTING);

    return temp.toFile().getAbsolutePath();
  }
}
