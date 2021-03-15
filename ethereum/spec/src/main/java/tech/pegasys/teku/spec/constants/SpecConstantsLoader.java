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

package tech.pegasys.teku.spec.constants;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.util.config.Constants;

public class SpecConstantsLoader {

  public static SpecConstants loadConstants(final String constants) {
    final SpecConstantsReader reader = new SpecConstantsReader();
    processConstants(constants, reader::read);
    return reader.build();
  }

  static void processConstants(final String source, final InputStreamProcessor processor) {
    // TODO(#3394) - move Constants resources from util to this module
    final ResourceLoader loader =
        ResourceLoader.classpathUrlOrFile(
            Constants.class, s -> s.endsWith(".yaml") || s.endsWith(".yml"));

    try {
      // Try to load single file format
      final Optional<InputStream> singleFileInput = loader.load(source + ".yaml", source);
      if (singleFileInput.isPresent()) {
        processor.process(singleFileInput.get());
        return;
      }

      // Otherwise, try multi-file format
      // Phase0 is required
      final InputStream phase0Input =
          loader
              .load(source + File.separator + "phase0.yaml", source + File.separator + "phase0.yml")
              .orElseThrow(
                  () -> new FileNotFoundException("Could not load constants from " + source));
      processor.process(phase0Input);
      // Altair is optional
      final Optional<InputStream> altairInput =
          loader.load(
              source + File.separator + "altair.yaml", source + File.separator + "altair.yml");
      if (altairInput.isPresent()) {
        processor.process(altairInput.get());
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load constants", e);
    }
  }

  interface InputStreamProcessor {
    void process(InputStream inputStream) throws IOException;
  }
}
