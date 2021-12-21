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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxCpuInfoTest {
  @Test
  void shouldReturnTrueWhenAllRequiredFeaturesArePresent() throws Exception {
    assertThat(allCpusHaveFeatures("allCpusHaveFeatures.txt", "bmi2", "adx")).isTrue();
  }

  @Test
  void shouldReturnFalseWhenRequiredFeaturesAreNotPresent() throws Exception {
    assertThat(allCpusHaveFeatures("allCpusHaveFeatures.txt", "adx", "nope")).isFalse();
  }

  @Test
  void shouldReturnFalseWhenSomeButNotAllCpusHaveFeatures() throws Exception {
    assertThat(allCpusHaveFeatures("cpusHaveDifferentFeatures.txt", "adx", "bmi2")).isFalse();
  }

  @Test
  void shouldReturnTrueWhenCpusHaveDifferentFeaturesButAllHaveRequiredFeature() throws Exception {
    assertThat(allCpusHaveFeatures("cpusHaveDifferentFeatures.txt", "fpu")).isTrue();
  }

  @Test
  void shouldReturnFalseWhenThereAreNoCpus() throws Exception {
    assertThat(LinuxCpuInfo.allCpusHaveFeatures(new StringReader(""), "fpu")).isFalse();
  }

  @Test
  void shouldThrowExceptionWhenFileDoesNotExist(@TempDir Path tempDir) {
    assertThatThrownBy(() -> LinuxCpuInfo.allCpusHaveFeatures(tempDir.resolve("nofile")))
        .isInstanceOf(IOException.class);
  }

  @Test
  void shouldReturnFalseWhenFlagsLineIsNotPresent() throws Exception {
    assertThat(allCpusHaveFeatures("missingFlagsLine.txt", "bmi2")).isFalse();
  }

  private boolean allCpusHaveFeatures(final String testFileName, final String... requiredFeatures)
      throws Exception {
    try (final Reader reader = loadCpuInfoFile(testFileName)) {
      return LinuxCpuInfo.allCpusHaveFeatures(reader, requiredFeatures);
    }
  }

  private Reader loadCpuInfoFile(final String filename) throws Exception {
    final URL resource = Resources.getResource(LinuxCpuInfoTest.class, "/cpuinfo/" + filename);
    return new BufferedReader(
        new InputStreamReader(resource.openStream(), StandardCharsets.US_ASCII));
  }
}
