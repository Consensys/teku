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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.spec.constants.SpecConstantsAssertions.assertAllAltairFieldsSet;
import static tech.pegasys.teku.spec.constants.SpecConstantsAssertions.assertAllFieldsSet;
import static tech.pegasys.teku.spec.constants.SpecConstantsAssertions.assertAllPhase0FieldsSet;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.util.config.Constants;

public class SpecConstantsLoaderTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("knownNetworks")
  public void shouldLoadAllKnownNetworks(final String name, final Class<?> constantsType)
      throws Exception {
    final SpecConstants constants = SpecConstantsLoader.loadConstants(name);
    assertAllFieldsSet(constants, constantsType);
  }

  @Test
  public void shouldLoadMainnet() throws Exception {
    final SpecConstants constants = SpecConstantsLoader.loadConstants("mainnet");
    assertAllAltairFieldsSet(constants);
  }

  @Test
  public void shouldLoadMainnetFromFileUrl() throws Exception {
    final URL url =
        Constants.class
            .getClassLoader()
            .getResource("tech/pegasys/teku/util/config/mainnet/phase0.yaml");
    final SpecConstants constants = SpecConstantsLoader.loadConstants(url.toString());
    assertAllPhase0FieldsSet(constants);
  }

  @Test
  public void shouldLoadMainnetFromDirectoryUrl() throws Exception {
    final String filePath =
        Constants.class
            .getClassLoader()
            .getResource("tech/pegasys/teku/util/config/mainnet/phase0.yaml")
            .toString();
    final String directoryPath = filePath.replace("/phase0.yaml", "");
    final SpecConstants constants = SpecConstantsLoader.loadConstants(directoryPath);
    assertAllAltairFieldsSet(constants);
  }

  @Test
  public void shouldTestAllKnownNetworks() {
    final List<String> testedNetworks =
        knownNetworks().map(args -> (String) args.get()[0]).sorted().collect(Collectors.toList());
    final List<String> allKnownNetworks =
        Arrays.stream(Eth2Network.values())
            .map(Eth2Network::constantsName)
            .sorted()
            .collect(Collectors.toList());

    assertThat(testedNetworks).isEqualTo(allKnownNetworks);
  }

  static Stream<Arguments> knownNetworks() {
    return Stream.of(
        Arguments.of(Eth2Network.MAINNET.constantsName(), SpecConstantsAltair.class),
        Arguments.of(Eth2Network.PYRMONT.constantsName(), SpecConstantsPhase0.class),
        Arguments.of(Eth2Network.MINIMAL.constantsName(), SpecConstantsAltair.class),
        Arguments.of(Eth2Network.SWIFT.constantsName(), SpecConstantsPhase0.class),
        Arguments.of(Eth2Network.LESS_SWIFT.constantsName(), SpecConstantsPhase0.class));
  }
}
