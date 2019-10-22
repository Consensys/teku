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
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.statetransition.util.EpochProcessorUtil;

@ExtendWith(BouncyCastleExtension.class)
public class crosslinks extends TestSuite {

  private static int testCounter = 0;

  @ParameterizedTest(name = "{index}. process crosslinks pre={0} -> post={1}")
  @MethodSource("mainnetCrosslinkSetup")
  void mainnetProcessCrosslinks(BeaconState pre, BeaconState post) throws Exception {
    EpochProcessorUtil.process_crosslinks(pre);
    assertEquals(pre, post);
  }

  @ParameterizedTest(name = "{index}. process crosslinks pre={0} -> post={1}")
  @MethodSource("minimalCrosslinkSetup")
  void minimalProcessCrosslinks(BeaconState pre, BeaconState post) throws Exception {
    EpochProcessorUtil.process_crosslinks(pre);
    assertEquals(pre, post);
  }

  @MustBeClosed
  static Stream<Arguments> crosslinkSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "epoch_processing", "crosslinks", "pyspec_tests");
    return epochProcessingSetup(path, Paths.get(config));
  }

  @MustBeClosed
  static Stream<Arguments> minimalCrosslinkSetup() throws Exception {
    return crosslinkSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetCrosslinkSetup() throws Exception {
    return crosslinkSetup("mainnet");
  }
}
