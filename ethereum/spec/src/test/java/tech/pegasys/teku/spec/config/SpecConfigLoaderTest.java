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

package tech.pegasys.teku.spec.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllAltairFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllPhase0FieldsSet;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.util.config.Constants;

public class SpecConfigLoaderTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("knownNetworks")
  public void shouldLoadAllKnownNetworks(final String name, final Class<?> configType)
      throws Exception {
    final SpecConfig config = SpecConfigLoader.loadConfig(name);
    assertAllFieldsSet(config, configType);
  }

  @Test
  public void shouldLoadMainnet() throws Exception {
    final SpecConfig config = SpecConfigLoader.loadConfig("mainnet");
    assertAllAltairFieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromFileUrl() throws Exception {
    final URL url =
        Constants.class
            .getClassLoader()
            .getResource("tech/pegasys/teku/util/config/mainnet/phase0.yaml");
    final SpecConfig config = SpecConfigLoader.loadConfig(url.toString());
    assertAllPhase0FieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromDirectoryUrl() throws Exception {
    final String filePath =
        Constants.class
            .getClassLoader()
            .getResource("tech/pegasys/teku/util/config/mainnet/phase0.yaml")
            .toString();
    final String directoryPath = filePath.replace("/phase0.yaml", "");
    final SpecConfig config = SpecConfigLoader.loadConfig(directoryPath);
    assertAllAltairFieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromFile(@TempDir Path tempDir) throws Exception {
    writeMainnetToFile(tempDir, "phase0.yaml");

    final Path file = tempDir.resolve("phase0.yaml");
    final SpecConfig config = SpecConfigLoader.loadConfig(file.toAbsolutePath().toString());
    assertAllPhase0FieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromDirectory(@TempDir Path tempDir) throws Exception {
    writeMainnetToFile(tempDir, "phase0.yaml");
    writeMainnetToFile(tempDir, "altair.yaml");

    final SpecConfig config = SpecConfigLoader.loadConfig(tempDir.toAbsolutePath().toString());
    assertAllAltairFieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetPhase0FromDirectory(@TempDir Path tempDir) throws Exception {
    writeMainnetToFile(tempDir, "phase0.yaml");

    final SpecConfig config = SpecConfigLoader.loadConfig(tempDir.toAbsolutePath().toString());
    assertAllPhase0FieldsSet(config);
  }

  @Test
  public void shouldTestAllKnownNetworks() {
    final List<String> testedNetworks =
        knownNetworks().map(args -> (String) args.get()[0]).sorted().collect(Collectors.toList());
    final List<String> allKnownNetworks =
        Arrays.stream(Eth2Network.values())
            .map(Eth2Network::configName)
            .sorted()
            .collect(Collectors.toList());

    assertThat(testedNetworks).isEqualTo(allKnownNetworks);
  }

  static Stream<Arguments> knownNetworks() {
    return Stream.of(
        Arguments.of(Eth2Network.MAINNET.configName(), SpecConfigAltair.class),
        Arguments.of(Eth2Network.PYRMONT.configName(), SpecConfigPhase0.class),
        Arguments.of(Eth2Network.PRATER.configName(), SpecConfigPhase0.class),
        Arguments.of(Eth2Network.MINIMAL.configName(), SpecConfigAltair.class),
        Arguments.of(Eth2Network.SWIFT.configName(), SpecConfigPhase0.class),
        Arguments.of(Eth2Network.LESS_SWIFT.configName(), SpecConfigPhase0.class));
  }

  private void writeMainnetToFile(final Path directory, final String configFile) throws Exception {
    final Path file = directory.resolve(configFile);
    final InputStream configStream =
        Constants.class
            .getClassLoader()
            .getResourceAsStream("tech/pegasys/teku/util/config/mainnet/" + configFile);
    byte[] buffer = new byte[configStream.available()];
    configStream.read(buffer);
    Files.write(file, buffer);
  }
}
