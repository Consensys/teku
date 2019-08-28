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

package pegasys.artemis.reference.mainnet.phase0;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@ExtendWith(BouncyCastleExtension.class)
class operations extends TestSuite {

  @ParameterizedTest(name = "{index}. process Attestation pre={0} -> post={1}")
  @MethodSource("processAttestationSetup")
  void processAttestation(Attestation attestation, BeaconState pre) throws Exception {
    //TODO
    int x = 0;
  }

  @MustBeClosed
  static Stream<Arguments> processAttestationSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("mainnet", "phase0", "operations", "attestation", "pyspec_tests");
    return attestationSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process AttesterSlashing pre={0} -> post={1}")
  @MethodSource("processAttestationSlashingSetup")
  void processAttesterSlashing(AttesterSlashing attesterSlashing, BeaconState pre) throws Exception {
    //TODO
    int x = 0;
  }

  @MustBeClosed
  static Stream<Arguments> processAttestationSlashingSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("mainnet", "phase0", "operations", "attester_slashing", "pyspec_tests");
    return attestationSlashingSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process BeaconBlock beaconBlock={0} -> pre={1}")
  @MethodSource("processBeaconBlockSetup")
  void processBeaconBlock(BeaconBlock beaconBlock, BeaconState pre) throws Exception {
    //TODO
    int x = 0;
  }

  @MustBeClosed
  static Stream<Arguments> processBeaconBlockSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("mainnet", "phase0", "operations", "block_header", "pyspec_tests", "invalid_parent_root");
    return blockHeaderInvalidParentRootSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process BeaconBlock beaconBlock={0} -> pre={1}")
  @MethodSource("processInvalidSignatureBlockHeaderSetup")
  void processBeaconBlock(BeaconBlock beaconBlock, Integer bls_setting, BeaconState pre) throws Exception {
    //TODO
    int x = 0;
  }

  @MustBeClosed
  static Stream<Arguments> processInvalidSignatureBlockHeaderSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("mainnet", "phase0", "operations", "block_header", "pyspec_tests", "invalid_sig_block_header");
    return invalidSignatureBlockHeaderSetup(path, configPath);
  }
}
