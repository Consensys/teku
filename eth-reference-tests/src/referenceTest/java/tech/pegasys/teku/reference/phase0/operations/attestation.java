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

package tech.pegasys.teku.reference.phase0.operations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.core.BlockProcessorUtil.process_attestations;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ethtests.TestSuite;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

@ExtendWith(BouncyCastleExtension.class)
public class attestation extends TestSuite {

  @ParameterizedTest(name = "{index}. process attestation success")
  @MethodSource({"mainnetAttestationSuccessSetup", "minimalAttestationSuccessSetup"})
  void processAttestationSuccess(Attestation attestation, BeaconState pre, BeaconState post) {
    BeaconState wState =
        assertDoesNotThrow(
            () ->
                pre.updated(state -> process_attestations(state, SSZList.singleton(attestation))));
    assertEquals(post, wState);
  }

  @ParameterizedTest(name = "{index}. process attestation")
  @MethodSource({"mainnetAttestationSetup", "minimalAttestationSetup"})
  void processAttestation(Attestation attestation, BeaconState pre) {
    assertThrows(
        BlockProcessingException.class,
        () -> pre.updated(state -> process_attestations(state, SSZList.singleton(attestation))));
  }

  @MustBeClosed
  static Stream<Arguments> attestationSuccessSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "attestation", "pyspec_tests");
    return operationSuccessSetup(path, Paths.get(config), "attestation.ssz", Attestation.class);
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

  @MustBeClosed
  static Stream<Arguments> minimalAttestationSuccessSetup() throws Exception {
    return attestationSuccessSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetAttestationSuccessSetup() throws Exception {
    return attestationSuccessSetup("mainnet");
  }
}
