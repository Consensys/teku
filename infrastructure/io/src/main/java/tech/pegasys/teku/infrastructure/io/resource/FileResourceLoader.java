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

package tech.pegasys.teku.infrastructure.io.resource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Predicate;

public class FileResourceLoader extends ResourceLoader {

  protected FileResourceLoader(final Predicate<String> sourceFilter) {
    super(sourceFilter);
  }

  @Override
  Optional<InputStream> loadSource(final String source) throws IOException {
    final File file = new File(source);
    if (!file.exists()) {
      return Optional.empty();
    }
    return Optional.of(new BufferedInputStream(new FileInputStream(file)));
  }
}
