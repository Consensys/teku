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

package pegasys.artemis.reference.phase0.operations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_attester_slashings;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;

@ExtendWith(BouncyCastleExtension.class)
public class attester_slashing extends TestSuite {

  @ParameterizedTest(name = "{index}. process AttesterSlashing attesterSlashing={0} -> pre={1}")
  @MethodSource("processAttestationSlashingSetup")
  void processAttesterSlashing(AttesterSlashing attesterSlashing, BeaconState pre)
      throws Exception {

    List<AttesterSlashing> attesterSlashings = new ArrayList<AttesterSlashing>();
    attesterSlashings.add(attesterSlashing);
    assertThrows(
        BlockProcessingException.class,
        () -> {
          process_attester_slashings(pre, attesterSlashings);
        });
  }

  @MustBeClosed
  static Stream<Arguments> processAttestationSlashingSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("mainnet", "phase0", "operations", "attester_slashing", "pyspec_tests");
    return attestationSlashingSetup(path, configPath);
  }
}
