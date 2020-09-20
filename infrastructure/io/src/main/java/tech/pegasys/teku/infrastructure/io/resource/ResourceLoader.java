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

package tech.pegasys.teku.infrastructure.io.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;

public interface ResourceLoader {

  static ResourceLoader urlOrFile() {
    return new FallbackResourceLoader(new URLResourceLoader(), new FileResourceLoader());
  }

  static ResourceLoader classpathUrlOrFile(
      final Class<?> referenceClass,
      final Function<String, String> nameToFilenameMapper,
      final String... availableResourceNames) {
    return new FallbackResourceLoader(
        new ClasspathResourceLoader(referenceClass, nameToFilenameMapper, availableResourceNames),
        new URLResourceLoader(),
        new FileResourceLoader());
  }

  Optional<InputStream> load(String source) throws IOException;

  default Optional<Bytes> loadBytes(String source) throws IOException {
    final Optional<InputStream> maybeStream = load(source);
    if (maybeStream.isEmpty()) {
      return Optional.empty();
    }
    try (InputStream in = maybeStream.get()) {
      return Optional.of(Bytes.wrap(in.readAllBytes()));
    }
  }
}
