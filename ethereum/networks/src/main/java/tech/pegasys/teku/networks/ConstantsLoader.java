/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.constants.SpecConstantsReader;
import tech.pegasys.teku.util.config.Constants;

public class ConstantsLoader {

  public static SpecConstants loadConstants(final String constants) {
    try (final InputStream inputStream = createInputStream(constants)) {
      final SpecConstantsReader reader = new SpecConstantsReader();
      return reader.read(inputStream);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load constants", e);
    }
  }

  private static InputStream createInputStream(final String source) throws IOException {
    // TODO(#3394) - move Constants resources from util to this module
    return ResourceLoader.classpathUrlOrFile(
            Constants.class,
            name -> name + ".yaml",
            Constants.NETWORK_DEFINITIONS.toArray(String[]::new))
        .load(source)
        .orElseThrow(() -> new FileNotFoundException("Could not load constants from " + source));
  }
}
