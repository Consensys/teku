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

import com.google.common.collect.ImmutableMap;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkData;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.util.config.SpecDependent;

public class SszTestExecutorDeprecated<T extends SszData> implements TestExecutor {
  private final Supplier<SszSchema<T>> sszType;

  public static ImmutableMap<String, TestExecutor> SSZ_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          // SSZ Static
          .put(
              "ssz_static/AggregateAndProof",
              new SszTestExecutorDeprecated<>(AggregateAndProof.SSZ_SCHEMA))
          .put("ssz_static/Attestation", new SszTestExecutorDeprecated<>(Attestation.SSZ_SCHEMA))
          .put(
              "ssz_static/AttestationData",
              new SszTestExecutorDeprecated<>(AttestationData.SSZ_SCHEMA))
          .put(
              "ssz_static/AttesterSlashing",
              new SszTestExecutorDeprecated<>(AttesterSlashing.SSZ_SCHEMA))
          .put(
              "ssz_static/BeaconBlockHeader",
              new SszTestExecutorDeprecated<>(BeaconBlockHeader.SSZ_SCHEMA))
          .put("ssz_static/Checkpoint", new SszTestExecutorDeprecated<>(Checkpoint.SSZ_SCHEMA))
          .put("ssz_static/Deposit", new SszTestExecutorDeprecated<>(Deposit.SSZ_SCHEMA))
          .put("ssz_static/DepositData", new SszTestExecutorDeprecated<>(DepositData.SSZ_SCHEMA))
          .put(
              "ssz_static/DepositMessage",
              new SszTestExecutorDeprecated<>(DepositMessage.SSZ_SCHEMA))
          .put("ssz_static/Eth1Block", IGNORE_TESTS) // We don't have an Eth1Block structure
          .put("ssz_static/Eth1Data", new SszTestExecutorDeprecated<>(Eth1Data.SSZ_SCHEMA))
          .put("ssz_static/Fork", new SszTestExecutorDeprecated<>(Fork.SSZ_SCHEMA))
          .put("ssz_static/ForkData", new SszTestExecutorDeprecated<>(ForkData.SSZ_SCHEMA))
          .put(
              "ssz_static/HistoricalBatch",
              new SszTestExecutorDeprecated<>(HistoricalBatch.SSZ_SCHEMA))
          .put(
              "ssz_static/IndexedAttestation",
              new SszTestExecutorDeprecated<>(IndexedAttestation.SSZ_SCHEMA))
          .put(
              "ssz_static/PendingAttestation",
              new SszTestExecutorDeprecated<>(PendingAttestation.SSZ_SCHEMA))
          .put(
              "ssz_static/ProposerSlashing",
              new SszTestExecutorDeprecated<>(ProposerSlashing.SSZ_SCHEMA))
          .put(
              "ssz_static/SignedAggregateAndProof",
              new SszTestExecutorDeprecated<>(SignedAggregateAndProof.SSZ_SCHEMA))
          .put(
              "ssz_static/SignedBeaconBlockHeader",
              new SszTestExecutorDeprecated<>(SignedBeaconBlockHeader.SSZ_SCHEMA))
          .put(
              "ssz_static/SignedVoluntaryExit",
              new SszTestExecutorDeprecated<>(SignedVoluntaryExit.SSZ_SCHEMA))
          .put("ssz_static/SigningData", new SszTestExecutorDeprecated<>(SigningData.SSZ_SCHEMA))
          .put("ssz_static/Validator", new SszTestExecutorDeprecated<>(Validator.SSZ_SCHEMA))
          .put(
              "ssz_static/VoluntaryExit", new SszTestExecutorDeprecated<>(VoluntaryExit.SSZ_SCHEMA))
          .build();

  public SszTestExecutorDeprecated(final SpecDependent<? extends SszSchema<T>> sszType) {
    this.sszType = sszType::get;
  }

  public SszTestExecutorDeprecated(final SszSchema<T> sszType) {
    this.sszType = () -> sszType;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final Bytes inputData = TestDataUtils.readSszData(testDefinition, "serialized.ssz_snappy");
    final Bytes32 expectedRoot =
        TestDataUtils.loadYaml(testDefinition, "roots.yaml", Roots.class).getRoot();
    final T result = sszType.get().sszDeserialize(inputData);

    // Deserialize
    assertThat(result.hashTreeRoot()).isEqualTo(expectedRoot);

    // Serialize
    assertThat(result.sszSerialize()).isEqualTo(inputData);
  }
}
