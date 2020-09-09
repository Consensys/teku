/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.reference.phase0.ssz_static;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkData;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.SigningData;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestDataUtils;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class SszTestExecutor<T extends SimpleOffsetSerializable & Merkleizable>
    implements TestExecutor {
  private final Class<T> clazz;

  public static ImmutableMap<String, TestExecutor> SSZ_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          // SSZ Static
          .put("ssz_static/AggregateAndProof", new SszTestExecutor<>(AggregateAndProof.class))
          .put("ssz_static/Attestation", new SszTestExecutor<>(Attestation.class))
          .put("ssz_static/AttestationData", new SszTestExecutor<>(AttestationData.class))
          .put("ssz_static/AttesterSlashing", new SszTestExecutor<>(AttesterSlashing.class))
          .put("ssz_static/BeaconBlock", new SszTestExecutor<>(BeaconBlock.class))
          .put("ssz_static/BeaconBlockBody", new SszTestExecutor<>(BeaconBlockBody.class))
          .put("ssz_static/BeaconBlockHeader", new SszTestExecutor<>(BeaconBlockHeader.class))
          .put("ssz_static/BeaconState", new SszTestExecutor<>(BeaconStateImpl.class))
          .put("ssz_static/Checkpoint", new SszTestExecutor<>(Checkpoint.class))
          .put("ssz_static/Deposit", new SszTestExecutor<>(Deposit.class))
          .put("ssz_static/DepositData", new SszTestExecutor<>(DepositData.class))
          .put("ssz_static/DepositMessage", new SszTestExecutor<>(DepositMessage.class))
          .put("ssz_static/Eth1Block", IGNORE_TESTS) // We don't have an Eth1Block structure
          .put("ssz_static/Eth1Data", new SszTestExecutor<>(Eth1Data.class))
          .put("ssz_static/Fork", new SszTestExecutor<>(Fork.class))
          .put("ssz_static/ForkData", new SszTestExecutor<>(ForkData.class))
          .put("ssz_static/HistoricalBatch", new SszTestExecutor<>(HistoricalBatch.class))
          .put("ssz_static/IndexedAttestation", new SszTestExecutor<>(IndexedAttestation.class))
          .put("ssz_static/PendingAttestation", new SszTestExecutor<>(PendingAttestation.class))
          .put("ssz_static/ProposerSlashing", new SszTestExecutor<>(ProposerSlashing.class))
          .put(
              "ssz_static/SignedAggregateAndProof",
              new SszTestExecutor<>(SignedAggregateAndProof.class))
          .put("ssz_static/SignedBeaconBlock", new SszTestExecutor<>(SignedBeaconBlock.class))
          .put(
              "ssz_static/SignedBeaconBlockHeader",
              new SszTestExecutor<>(SignedBeaconBlockHeader.class))
          .put("ssz_static/SignedVoluntaryExit", new SszTestExecutor<>(SignedVoluntaryExit.class))
          .put("ssz_static/SigningData", new SszTestExecutor<>(SigningData.class))
          .put("ssz_static/Validator", new SszTestExecutor<>(Validator.class))
          .put("ssz_static/VoluntaryExit", new SszTestExecutor<>(VoluntaryExit.class))

          // SSZ Generic
          .put("ssz_generic/basic_vector", IGNORE_TESTS)
          .put("ssz_generic/bitlist", IGNORE_TESTS)
          .put("ssz_generic/bitvector", IGNORE_TESTS)
          .put("ssz_generic/boolean", IGNORE_TESTS)
          .put("ssz_generic/containers", IGNORE_TESTS)
          .put("ssz_generic/uints", IGNORE_TESTS)
          .build();

  public SszTestExecutor(final Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final Path testDirectory = testDefinition.getTestDirectory();
    final Bytes inputData = Bytes.wrap(Files.readAllBytes(testDirectory.resolve("serialized.ssz")));
    final Bytes32 expectedRoot =
        TestDataUtils.loadYaml(testDefinition, "roots.yaml", Roots.class).getRoot();
    final T result = SimpleOffsetSerializer.deserialize(inputData, clazz);

    // Deserialize
    assertThat(result.hash_tree_root()).isEqualTo(expectedRoot);

    // Serialize
    assertThat(SimpleOffsetSerializer.serialize(result)).isEqualTo(inputData);
  }

  private static class Roots {
    @JsonProperty(value = "root", required = true)
    private String root;

    public Bytes32 getRoot() {
      return Bytes32.fromHexString(root);
    }
  }
}
