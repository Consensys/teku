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
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestDataUtils;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public class SszTestExecutor<T extends SszData> implements TestExecutor {
  private static final Spec spec = SpecFactory.createMinimal();
  private final SchemaProvider<T> sszType;

  public static ImmutableMap<String, TestExecutor> SSZ_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          // SSZ Static
          .put(
              "ssz_static/BeaconState",
              new SszTestExecutor<>(SchemaDefinitions::getBeaconStateSchema))
          // SSZ Generic
          .put("ssz_generic/basic_vector", IGNORE_TESTS)
          .put("ssz_generic/bitlist", IGNORE_TESTS)
          .put("ssz_generic/bitvector", IGNORE_TESTS)
          .put("ssz_generic/boolean", IGNORE_TESTS)
          .put("ssz_generic/containers", IGNORE_TESTS)
          .put("ssz_generic/uints", IGNORE_TESTS)
          .build();

  public SszTestExecutor(final SchemaProvider<T> sszType) {
    this.sszType = sszType;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final Path testDirectory = testDefinition.getTestDirectory();
    final Bytes inputData = Bytes.wrap(Files.readAllBytes(testDirectory.resolve("serialized.ssz")));
    final Bytes32 expectedRoot =
        TestDataUtils.loadYaml(testDefinition, "roots.yaml", Roots.class).getRoot();
    final SchemaDefinitions schemaDefinitions =
        testDefinition.getSpec().getGenesisSpec().getSchemaDefinitions();
    final T result = sszType.get(schemaDefinitions).sszDeserialize(inputData);

    // Deserialize
    assertThat(result.hashTreeRoot()).isEqualTo(expectedRoot);

    // Serialize
    assertThat(result.sszSerialize()).isEqualTo(inputData);
  }

  private interface SchemaProvider<T extends SszData> {
    SszSchema<T> get(SchemaDefinitions schemas);
  }
}
