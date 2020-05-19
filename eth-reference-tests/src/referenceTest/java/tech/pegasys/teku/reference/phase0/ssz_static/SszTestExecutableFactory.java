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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.function.Executable;
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
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.ExecutableFactory;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.hashtree.Merkleizable;

public class SszTestExecutableFactory<T extends SimpleOffsetSerializable & Merkleizable>
    implements ExecutableFactory {
  private final Class<T> clazz;

  public static ImmutableMap<String, ExecutableFactory> SSZ_TEST_TYPES =
      ImmutableMap.<String, ExecutableFactory>builder()
          // SSZ Static
          .put(
              "ssz_static/AggregateAndProof",
              new SszTestExecutableFactory<>(AggregateAndProof.class))
          .put("ssz_static/Attestation", new SszTestExecutableFactory<>(Attestation.class))
          .put("ssz_static/AttestationData", new SszTestExecutableFactory<>(AttestationData.class))
          .put(
              "ssz_static/AttesterSlashing", new SszTestExecutableFactory<>(AttesterSlashing.class))
          .put("ssz_static/BeaconBlock", new SszTestExecutableFactory<>(BeaconBlock.class))
          .put("ssz_static/BeaconBlockBody", new SszTestExecutableFactory<>(BeaconBlockBody.class))
          .put(
              "ssz_static/BeaconBlockHeader",
              new SszTestExecutableFactory<>(BeaconBlockHeader.class))
          .put("ssz_static/BeaconState", new SszTestExecutableFactory<>(BeaconStateImpl.class))
          .put("ssz_static/Checkpoint", new SszTestExecutableFactory<>(Checkpoint.class))
          .put("ssz_static/Deposit", new SszTestExecutableFactory<>(Deposit.class))
          .put("ssz_static/DepositData", new SszTestExecutableFactory<>(DepositData.class))
          .put("ssz_static/DepositMessage", new SszTestExecutableFactory<>(DepositMessage.class))
          .put("ssz_static/Eth1Block", IGNORE_TESTS) // We don't have an Eth1Block structure
          .put("ssz_static/Eth1Data", new SszTestExecutableFactory<>(Eth1Data.class))
          .put("ssz_static/Fork", new SszTestExecutableFactory<>(Fork.class))
          .put("ssz_static/ForkData", new SszTestExecutableFactory<>(ForkData.class))
          .put("ssz_static/HistoricalBatch", new SszTestExecutableFactory<>(HistoricalBatch.class))
          .put(
              "ssz_static/IndexedAttestation",
              new SszTestExecutableFactory<>(IndexedAttestation.class))
          .put(
              "ssz_static/PendingAttestation",
              new SszTestExecutableFactory<>(PendingAttestation.class))
          .put(
              "ssz_static/ProposerSlashing", new SszTestExecutableFactory<>(ProposerSlashing.class))
          .put(
              "ssz_static/SignedAggregateAndProof",
              new SszTestExecutableFactory<>(SignedAggregateAndProof.class))
          .put(
              "ssz_static/SignedBeaconBlock",
              new SszTestExecutableFactory<>(SignedBeaconBlock.class))
          .put(
              "ssz_static/SignedBeaconBlockHeader",
              new SszTestExecutableFactory<>(SignedBeaconBlockHeader.class))
          .put(
              "ssz_static/SignedVoluntaryExit",
              new SszTestExecutableFactory<>(SignedVoluntaryExit.class))
          .put("ssz_static/SigningRoot", IGNORE_TESTS) // TODO: Should make this work
          .put("ssz_static/Validator", new SszTestExecutableFactory<>(Validator.class))
          .put("ssz_static/VoluntaryExit", new SszTestExecutableFactory<>(VoluntaryExit.class))

          // SSZ Generic
          .put("ssz_generic/basic_vector", IGNORE_TESTS)
          .put("ssz_generic/bitlist", IGNORE_TESTS)
          .put("ssz_generic/bitvector", IGNORE_TESTS)
          .put("ssz_generic/boolean", IGNORE_TESTS)
          .put("ssz_generic/containers", IGNORE_TESTS)
          .put("ssz_generic/uints", IGNORE_TESTS)
          .build();

  public SszTestExecutableFactory(final Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public Executable forTestDefinition(final TestDefinition testDefinition) {
    return () -> {
      final Path testDirectory = testDefinition.getTestDirectory();
      final Bytes inputData =
          Bytes.wrap(Files.readAllBytes(testDirectory.resolve("serialized.ssz")));
      final Bytes32 expectedRoot = loadExpectedRoot(testDirectory.resolve("roots.yaml"));
      final T result = SimpleOffsetSerializer.deserialize(inputData, clazz);

      // Deserialize
      assertThat(result.hash_tree_root()).isEqualTo(expectedRoot);

      // Serialize
      assertThat(SimpleOffsetSerializer.serialize(result)).isEqualTo(inputData);
    };
  }

  private Bytes32 loadExpectedRoot(final Path rootsYaml) throws IOException {
    try (final InputStream in = Files.newInputStream(rootsYaml)) {
      final Map<String, String> data =
          new ObjectMapper(new YAMLFactory()).readerFor(Map.class).readValue(in);
      return Bytes32.fromHexString(data.get("root"));
    }
  }
}
