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

package tech.pegasys.artemis.reference.phase0.operations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_attestations;

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
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;

@ExtendWith(BouncyCastleExtension.class)
public class attestation extends TestSuite {

  @ParameterizedTest(name = "{index}. mainnet process attestation: {4}")
  @MethodSource("mainnetAttestationSetup")
  void mainnetProcessAttestation(
      Attestation attestation,
      BeaconState pre,
      BeaconState post,
      Boolean succesTest,
      String testName)
      throws Exception {
    List<Attestation> attestations = new ArrayList<>();
    attestations.add(attestation);
    if (succesTest) {
      assertDoesNotThrow(() -> process_attestations(pre, attestations));
      assertEquals(pre, post);
    } else {
      assertThrows(BlockProcessingException.class, () -> process_attestations(pre, attestations));
    }
  }

  @ParameterizedTest(name = "{index}. minimal process attestation: {4}")
  @MethodSource("minimalAttestationSetup")
  void minimalProcessAttestation(
      Attestation attestation,
      BeaconState pre,
      BeaconState post,
      Boolean succesTest,
      String testName)
      throws Exception {
    List<Attestation> attestations = new ArrayList<>();
    attestations.add(attestation);
    if (succesTest) {
      assertDoesNotThrow(() -> process_attestations(pre, attestations));
      assertEquals(pre, post);
    } else {
      assertThrows(BlockProcessingException.class, () -> process_attestations(pre, attestations));
    }
  }

  @MustBeClosed
  static Stream<Arguments> attestationSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "attestation", "pyspec_tests");
    return operationSetup(path, Paths.get(config), "attestation.ssz", Attestation.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalAttestationSetup() throws Exception {
    return attestationSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetAttestationSetup() throws Exception {
    return attestationSetup("mainnet");
  }
}
