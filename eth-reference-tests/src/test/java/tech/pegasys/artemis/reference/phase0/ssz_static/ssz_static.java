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

package tech.pegasys.artemis.reference.phase0.ssz_static;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.CompactCommittee;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.hashtree.SigningRoot;

@ExtendWith(BouncyCastleExtension.class)
@Disabled
public class ssz_static extends TestSuite {

  @ParameterizedTest(name = "{index} root of Merkleizable")
  @MethodSource({
    "readMessageSSZAttestationData",
    "readMessageSSZAttestationDataAndCustodyBit",
    "readMessageSSZAttesterSlashing",
    "readMessageSSZBeaconBlockBody",
    "readMessageSSZBeaconState",
    "readMessageSSZCheckpoint",
    "readMessageSSZCompactComitee",
    "readMessageSSZCrosslink",
    "readMessageSSZDeposit",
    "readMessageSSZEth1Data",
    "readMessageSSZFork",
    "readMessageSSZHistoricalBatch",
    "readMessageSSZPendingAttestation",
    "readMessageSSZProposerSlashing",
    "readMessageSSZValidator"
  })
  void sszCheckRootAndSigningRoot(Merkleizable merkleizable, Bytes32 root) {
    assertEquals(
        merkleizable.hash_tree_root(),
        root,
        merkleizable.getClass().getName() + " failed the root test");
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestationData() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/AttestationData/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, AttestationData.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestationDataAndCustodyBit() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/AttestationDataAndCustodyBit/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, AttestationDataAndCustodyBit.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttesterSlashing() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/AttesterSlashing/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, AttesterSlashing.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlockBody() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/BeaconBlockBody/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, BeaconBlockBody.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconState() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/BeaconState/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, BeaconState.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZCheckpoint() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Checkpoint/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Checkpoint.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZCompactComitee() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/CompactCommittee/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, CompactCommittee.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZCrosslink() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Crosslink/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Crosslink.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZDeposit() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Deposit/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Deposit.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZEth1Data() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Eth1Data/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Eth1Data.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZFork() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Fork/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Fork.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZHistoricalBatch() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/HistoricalBatch/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, HistoricalBatch.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZPendingAttestation() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/PendingAttestation/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, PendingAttestation.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZProposerSlashing() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/ProposerSlashing/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, ProposerSlashing.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZValidator() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Validator/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Validator.class);
  }

  @ParameterizedTest(name = "{index} check root and signing_root")
  @MethodSource({
    "readMessageSSZAttestation",
    "readMessageSSZBeaconBlock",
    "readMessageSSZBeaconBlockHeader",
    "readMessageSSZDepositData",
    "readMessageSSZIndexedAttestation",
    "readMessageSSZTransfer",
    "readMessageSSZVoluntaryExit"
  })
  void sszCheckRootAndSigningRoot(Merkleizable merkleizable, Bytes32 root, Bytes32 signing_root) {
    assertEquals(
        merkleizable.hash_tree_root(),
        root,
        merkleizable.getClass().getName() + " failed the root test");
    assertEquals(
        ((SigningRoot) merkleizable).signing_root("signature"),
        signing_root,
        merkleizable.getClass().getName() + " failed the root test");
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestation() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Attestation/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, Attestation.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlock() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/BeaconBlock/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, BeaconBlock.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlockHeader() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/BeaconBlockHeader/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, BeaconBlockHeader.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZDepositData() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/DepositData/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, DepositData.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZIndexedAttestation() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/IndexedAttestation/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, IndexedAttestation.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZTransfer() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Transfer/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, Transfer.class);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageSSZVoluntaryExit() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/VoluntaryExit/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, VoluntaryExit.class);
  }
}
