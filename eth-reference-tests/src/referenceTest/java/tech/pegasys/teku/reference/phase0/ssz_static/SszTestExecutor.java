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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;

public class SszTestExecutor<T extends SszData> implements TestExecutor {
  private final SchemaProvider<T> sszType;

  public static ImmutableMap<String, TestExecutor> SSZ_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          // SSZ Static
          .put(
              "ssz_static/BeaconState",
              new SszTestExecutor<>(SchemaDefinitions::getBeaconStateSchema))
          .put(
              "ssz_static/SignedBeaconBlock",
              new SszTestExecutor<>(SchemaDefinitions::getSignedBeaconBlockSchema))
          .put(
              "ssz_static/BeaconBlock",
              new SszTestExecutor<>(SchemaDefinitions::getBeaconBlockSchema))
          .put(
              "ssz_static/BeaconBlockBody",
              new SszTestExecutor<>(SchemaDefinitions::getBeaconBlockBodySchema))
          .put(
              "ssz_static/SyncCommittee",
              new SszTestExecutor<>(
                  schemas -> schemas.getBeaconStateSchema().getCurrentSyncCommitteeSchemaOrThrow()))
          .put(
              "ssz_static/SyncAggregate",
              new SszTestExecutor<>(
                  schemas ->
                      BeaconBlockBodySchemaAltair.required(schemas.getBeaconBlockBodySchema())
                          .getSyncAggregateSchema()))
          .put(
              "ssz_static/SyncCommitteeContribution",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsAltair.required(schemas)
                          .getSyncCommitteeContributionSchema()))
          .put(
              "ssz_static/ContributionAndProof",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsAltair.required(schemas).getContributionAndProofSchema()))
          .put(
              "ssz_static/SignedContributionAndProof",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsAltair.required(schemas)
                          .getSignedContributionAndProofSchema()))
          .put(
              "ssz_static/SyncCommitteeMessage",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsAltair.required(schemas).getSyncCommitteeMessageSchema()))
          .put(
              "ssz_static/SyncAggregatorSelectionData",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsAltair.required(schemas)
                          .getSyncAggregatorSelectionDataSchema()))
          .put("ssz_static/LightClientStore", IGNORE_TESTS)
          .put("ssz_static/LightClientSnapshot", IGNORE_TESTS)
          .put("ssz_static/LightClientUpdate", IGNORE_TESTS)

          // Merge types
          .put(
              "ssz_static/ExecutionPayloadHeader",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsMerge.required(schemas).getExecutionPayloadHeaderSchema()))
          .put(
              "ssz_static/ExecutionPayload",
              new SszTestExecutor<>(
                  schemas -> SchemaDefinitionsMerge.required(schemas).getExecutionPayloadSchema()))
          .put("ssz_static/PowBlock", IGNORE_TESTS)
          .build();

  public SszTestExecutor(final SchemaProvider<T> sszType) {
    this.sszType = sszType;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final Bytes inputData = TestDataUtils.readSszData(testDefinition, "serialized.ssz_snappy");
    final Bytes32 expectedRoot =
        TestDataUtils.loadYaml(testDefinition, "roots.yaml", Roots.class).getRoot();
    final SchemaDefinitions schemaDefinitions =
        testDefinition.getSpec().getGenesisSchemaDefinitions();
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
