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

package tech.pegasys.teku.bls.impl.blst;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.IOUtils;

public class LinuxCpuInfo {

  public static boolean supportsOptimisedBlst() throws IOException {
    return allCpusHaveFeatures("bmi2", "adx");
  }

  public static boolean allCpusHaveFeatures(final String... requiredFeatures) throws IOException {
    return allCpusHaveFeatures(Path.of("/proc/cpuinfo"), requiredFeatures);
  }

  static boolean allCpusHaveFeatures(final Path cpuInfoPath, final String... requiredFeatures)
      throws IOException {
    return allCpusHaveFeatures(Files.newBufferedReader(cpuInfoPath), requiredFeatures);
  }

  static boolean allCpusHaveFeatures(final Reader in, final String... requiredFeatures)
      throws IOException {
    checkArgument(requiredFeatures.length > 0, "Must require some features");
    final List<String> lines = IOUtils.readLines(in);
    // All CPUs must support the required instruction sets.
    return lines.stream()
        .filter(LinuxCpuInfo::isFlagsLine)
        .map(flagLine -> hasAllRequiredFeatures(flagLine, requiredFeatures))
        .reduce(Boolean::logicalAnd)
        .orElse(false);
  }

  private static boolean isFlagsLine(final String line) {
    return line.matches("^flags\\s*:.*");
  }

  private static boolean hasAllRequiredFeatures(
      final String flagLine, final String... requiredFeatures) {
    final Set<String> flags = Set.of(flagLine.substring(flagLine.indexOf(':') + 1).split("\\s"));
    for (String requiredFeature : requiredFeatures) {
      if (!flags.contains(requiredFeature)) {
        return false;
      }
    }
    return true;
  }
}
