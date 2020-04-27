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

package tech.pegasys.teku.reference.phase0.shuffling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.compute_shuffled_index;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ethtests.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
public class shuffleMinimal extends TestSuite {

  @ParameterizedTest(name = "{index} Sanity shuffling (Minimal)")
  @MethodSource({"shufflingGenericShuffleSetup"})
  void sanityProcessShuffling(Bytes32 seed, Integer count, List<Integer> mapping) {
    IntStream.range(0, count)
        .forEach(
            i -> {
              assertEquals(compute_shuffled_index(i, count, seed), mapping.get(i));
            });
  }

  @MustBeClosed
  static Stream<Arguments> shufflingGenericShuffleSetup() throws Exception {
    Path configPath = Paths.get("minimal", "phase0");
    Path path = Paths.get("/minimal/phase0/shuffling/core/shuffle");
    return shufflingShuffleSetup(path, configPath);
  }
}
