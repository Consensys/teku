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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.util.config.Constants;

public class SpecConstantsLoader {

  public static SpecConstants loadConstants(final String constants) {
    try (final InputStream inputStream = createInputStream(constants)) {
      final SpecConstantsReader reader = new SpecConstantsReader();
      return reader.read(inputStream);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load constants", e);
    }
  }

  static InputStream createInputStream(final String source) throws IOException {
    // TODO(#3394) - move Constants resources from util to this module
    final List<InputStream> inputStreams =
        ResourceLoader.classpathUrlOrFile(
                Constants.class,
                name -> List.of(name + ".yaml", name + "/phase0.yaml", name + "/altair.yaml"),
                networkOptions())
            .loadAll(source);

    if (inputStreams.isEmpty()) {
      throw new FileNotFoundException("Could not load constants from " + source);
    }

    // Make sure we have newline separators between input streams
    final List<InputStream> inputStreamsWithNewLines = new ArrayList<>();
    for (InputStream inputStream : inputStreams) {
      inputStreamsWithNewLines.add(inputStream);
      inputStreamsWithNewLines.add(new ByteArrayInputStream("\n".getBytes(UTF_8)));
    }
    return new SequenceInputStream(Collections.enumeration(inputStreamsWithNewLines));
  }

  private static String[] networkOptions() {
    return Arrays.stream(Eth2Network.values())
        .map(Eth2Network::constantsName)
        .toArray(String[]::new);
  }
}
