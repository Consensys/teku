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
public class justification_and_finalization extends TestSuite {
  @ParameterizedTest(name = "{index}.{2} process justification and finalization")
  @MethodSource("mainnetProcessJusticationAndFinalizationSetup")
  void mainnetProcessJusticationAndFinalization(BeaconState pre, BeaconState post, String testName)
      throws Exception {
    BeaconState wState = pre.updated(EpochProcessorUtil::process_justification_and_finalization);
    assertEquals(post, wState);
  }

  @ParameterizedTest(name = "{index}.{2} process justification and finalization")
  @MethodSource("minimalProcessJusticationAndFinalizationSetup")
  void minimalProcessJusticationAndFinalization(BeaconState pre, BeaconState post, String testName)
      throws Exception {
    BeaconState wState = pre.updated(EpochProcessorUtil::process_justification_and_finalization);
    assertEquals(post, wState);
  }

  @MustBeClosed
  static Stream<Arguments> justificationAndFinalizationSetup(String config) throws Exception {
    Path path =
        Paths.get(
            config, "phase0", "epoch_processing", "justification_and_finalization", "pyspec_tests");
    return epochProcessingSetup(path, Paths.get(config));
  }

  @MustBeClosed
  static Stream<Arguments> minimalProcessJusticationAndFinalizationSetup() throws Exception {
    return justificationAndFinalizationSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetProcessJusticationAndFinalizationSetup() throws Exception {
    return justificationAndFinalizationSetup("mainnet");
  }
}
