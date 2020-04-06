/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.reference.phase0.epoch_processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.core.EpochProcessorUtil;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.ethtests.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
public class registry_updates extends TestSuite {

  @ParameterizedTest(name = "{index}.{2} process registry updates")
  @MethodSource({
    "mainnetProcessRegistryUpdates",
  })
  void mainnetProcessRegistryUpdates(BeaconState pre, BeaconState post, String testName)
      throws Exception {
    BeaconState wState = pre.updated(EpochProcessorUtil::process_registry_updates);
    assertEquals(post, wState);
  }

  @ParameterizedTest(name = "{index}.{2} process registry updates")
  @MethodSource({
    "minimalProcessRegistryUpdates",
  })
  void minimalProcessRegistryUpdates(BeaconState pre, BeaconState post, String testName)
      throws Exception {
    BeaconState wState = pre.updated(EpochProcessorUtil::process_registry_updates);
    assertThat(wState).isEqualTo(post);
  }

  @MustBeClosed
  static Stream<Arguments> mainnetProcessRegistryUpdates() throws Exception {
    return processRegistryUpdates("mainnet");
  }

  @MustBeClosed
  static Stream<Arguments> minimalProcessRegistryUpdates() throws Exception {
    return processRegistryUpdates("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> processRegistryUpdates(String config) throws Exception {
    Path configPath = Paths.get(config, "phase0");
    Path path = Paths.get(config, "phase0", "epoch_processing", "registry_updates", "pyspec_tests");
    return epochProcessingSetup(path, configPath);
  }
}
