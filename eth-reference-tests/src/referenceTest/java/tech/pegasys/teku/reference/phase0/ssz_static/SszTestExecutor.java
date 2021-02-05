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
import java.util.function.Supplier;
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
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkData;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.SigningData;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.SpecDependent;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestDataUtils;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public class SszTestExecutor<T extends SszData> implements TestExecutor {
  private final Supplier<SszSchema<T>> sszType;

  public static ImmutableMap<String, TestExecutor> SSZ_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          // SSZ Static
          .put("ssz_static/AggregateAndProof", new SszTestExecutor<>(AggregateAndProof.SSZ_SCHEMA))
          .put("ssz_static/Attestation", new SszTestExecutor<>(Attestation.SSZ_SCHEMA))
          .put("ssz_static/AttestationData", new SszTestExecutor<>(AttestationData.SSZ_SCHEMA))
          .put("ssz_static/AttesterSlashing", new SszTestExecutor<>(AttesterSlashing.SSZ_SCHEMA))
          .put("ssz_static/BeaconBlock", new SszTestExecutor<>(BeaconBlock.SSZ_SCHEMA))
          .put("ssz_static/BeaconBlockBody", new SszTestExecutor<>(BeaconBlockBody.SSZ_SCHEMA))
          .put("ssz_static/BeaconBlockHeader", new SszTestExecutor<>(BeaconBlockHeader.SSZ_SCHEMA))
          .put("ssz_static/BeaconState", new SszTestExecutor<>(BeaconState.SSZ_SCHEMA))
          .put("ssz_static/Checkpoint", new SszTestExecutor<>(Checkpoint.SSZ_SCHEMA))
          .put("ssz_static/Deposit", new SszTestExecutor<>(Deposit.SSZ_SCHEMA))
          .put("ssz_static/DepositData", new SszTestExecutor<>(DepositData.SSZ_SCHEMA))
          .put("ssz_static/DepositMessage", new SszTestExecutor<>(DepositMessage.SSZ_SCHEMA))
          .put("ssz_static/Eth1Block", IGNORE_TESTS) // We don't have an Eth1Block structure
          .put("ssz_static/Eth1Data", new SszTestExecutor<>(Eth1Data.SSZ_SCHEMA))
          .put("ssz_static/Fork", new SszTestExecutor<>(Fork.SSZ_SCHEMA))
          .put("ssz_static/ForkData", new SszTestExecutor<>(ForkData.SSZ_SCHEMA))
          .put("ssz_static/HistoricalBatch", new SszTestExecutor<>(HistoricalBatch.SSZ_SCHEMA))
          .put("ssz_static/IndexedAttestation", new SszTestExecutor<>(IndexedAttestation.SSZ_SCHEMA))
          .put("ssz_static/PendingAttestation", new SszTestExecutor<>(PendingAttestation.SSZ_SCHEMA))
          .put("ssz_static/ProposerSlashing", new SszTestExecutor<>(ProposerSlashing.SSZ_SCHEMA))
          .put(
              "ssz_static/SignedAggregateAndProof",
              new SszTestExecutor<>(SignedAggregateAndProof.SSZ_SCHEMA))
          .put(
              "ssz_static/SignedBeaconBlock", new SszTestExecutor<>(SignedBeaconBlock.getSszType()))
          .put(
              "ssz_static/SignedBeaconBlockHeader",
              new SszTestExecutor<>(SignedBeaconBlockHeader.SSZ_SCHEMA))
          .put("ssz_static/SignedVoluntaryExit", new SszTestExecutor<>(SignedVoluntaryExit.SSZ_SCHEMA))
          .put("ssz_static/SigningData", new SszTestExecutor<>(SigningData.SSZ_SCHEMA))
          .put("ssz_static/Validator", new SszTestExecutor<>(Validator.SSZ_SCHEMA))
          .put("ssz_static/VoluntaryExit", new SszTestExecutor<>(VoluntaryExit.SSZ_SCHEMA))

          // SSZ Generic
          .put("ssz_generic/basic_vector", IGNORE_TESTS)
          .put("ssz_generic/bitlist", IGNORE_TESTS)
          .put("ssz_generic/bitvector", IGNORE_TESTS)
          .put("ssz_generic/boolean", IGNORE_TESTS)
          .put("ssz_generic/containers", IGNORE_TESTS)
          .put("ssz_generic/uints", IGNORE_TESTS)
          .build();

  public SszTestExecutor(final SpecDependent<? extends SszSchema<T>> sszType) {
    this.sszType = sszType::get;
  }

  public SszTestExecutor(final SszSchema<T> sszType) {
    this.sszType = () -> sszType;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final Path testDirectory = testDefinition.getTestDirectory();
    final Bytes inputData = Bytes.wrap(Files.readAllBytes(testDirectory.resolve("serialized.ssz")));
    final Bytes32 expectedRoot =
        TestDataUtils.loadYaml(testDefinition, "roots.yaml", Roots.class).getRoot();
    final T result = sszType.get().sszDeserialize(inputData);

    // Deserialize
    assertThat(result.hashTreeRoot()).isEqualTo(expectedRoot);

    // Serialize
    assertThat(result.sszSerialize()).isEqualTo(inputData);
  }

  private static class Roots {
    @JsonProperty(value = "root", required = true)
    private String root;

    public Bytes32 getRoot() {
      return Bytes32.fromHexString(root);
    }
  }
}
