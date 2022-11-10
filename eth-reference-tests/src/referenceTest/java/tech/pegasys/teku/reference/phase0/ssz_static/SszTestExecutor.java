/*
 * Copyright ConsenSys Software Inc., 2022
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
import static org.assertj.core.api.Assumptions.assumeThat;

import com.google.common.collect.ImmutableMap;
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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkData;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class SszTestExecutor<T extends SszData> implements TestExecutor {
  private final SchemaProvider<T> sszType;

  public static final ImmutableMap<String, TestExecutor> SSZ_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          // SSZ Static
          .put(
              "ssz_static/AggregateAndProof",
              new SszTestExecutor<>(SchemaDefinitions::getAggregateAndProofSchema))
          .put(
              "ssz_static/Attestation",
              new SszTestExecutor<>(SchemaDefinitions::getAttestationSchema))
          .put(
              "ssz_static/AttesterSlashing",
              new SszTestExecutor<>(SchemaDefinitions::getAttesterSlashingSchema))
          .put(
              "ssz_static/SignedAggregateAndProof",
              new SszTestExecutor<>(SchemaDefinitions::getSignedAggregateAndProofSchema))
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
              "ssz_static/HistoricalBatch",
              new SszTestExecutor<>(SchemaDefinitions::getHistoricalBatchSchema))
          .put(
              "ssz_static/IndexedAttestation",
              new SszTestExecutor<>(SchemaDefinitions::getIndexedAttestationSchema))
          .put(
              "ssz_static/PendingAttestation",
              new SszTestExecutor<>(
                  schemas -> {
                    assumeThat(schemas.getBeaconStateSchema())
                        .isInstanceOf(BeaconStateSchemaPhase0.class);
                    return BeaconStateSchemaPhase0.required(schemas.getBeaconStateSchema())
                        .getPendingAttestationSchema();
                  }))
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
          .put("ssz_static/LightClientBootstrap", IGNORE_TESTS)
          .put("ssz_static/LightClientFinalityUpdate", IGNORE_TESTS)
          .put("ssz_static/LightClientOptimisticUpdate", IGNORE_TESTS)
          .put("ssz_static/LightClientStore", IGNORE_TESTS)
          .put("ssz_static/LightClientSnapshot", IGNORE_TESTS)
          .put("ssz_static/LightClientUpdate", IGNORE_TESTS)

          // Bellatrix types
          .put(
              "ssz_static/ExecutionPayloadHeader",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsBellatrix.required(schemas)
                          .getExecutionPayloadHeaderSchema()))
          .put(
              "ssz_static/ExecutionPayload",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsBellatrix.required(schemas).getExecutionPayloadSchema()))
          .put("ssz_static/PowBlock", IGNORE_TESTS)

          // Capella Types
          .put(
              "ssz_static/SignedBLSToExecutionChange",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsCapella.required(schemas)
                          .getSignedBlsToExecutionChangeSchema()))
          .put(
              "ssz_static/BLSToExecutionChange",
              new SszTestExecutor<>(
                  schemas ->
                      SchemaDefinitionsCapella.required(schemas).getBlsToExecutionChangeSchema()))
          .put(
              "ssz_static/Withdrawal",
              new SszTestExecutor<>(
                  schemas -> SchemaDefinitionsCapella.required(schemas).getWithdrawalSchema()))

          // Legacy Schemas (Not yet migrated to SchemaDefinitions)
          .put(
              "ssz_static/AttestationData", new SszTestExecutor<>(__ -> AttestationData.SSZ_SCHEMA))
          .put(
              "ssz_static/BeaconBlockHeader",
              new SszTestExecutor<>(__ -> BeaconBlockHeader.SSZ_SCHEMA))
          .put("ssz_static/Checkpoint", new SszTestExecutor<>(__ -> Checkpoint.SSZ_SCHEMA))
          .put("ssz_static/Deposit", new SszTestExecutor<>(__ -> Deposit.SSZ_SCHEMA))
          .put("ssz_static/DepositData", new SszTestExecutor<>(__ -> DepositData.SSZ_SCHEMA))
          .put("ssz_static/DepositMessage", new SszTestExecutor<>(__ -> DepositMessage.SSZ_SCHEMA))
          .put("ssz_static/Eth1Block", IGNORE_TESTS) // We don't have an Eth1Block structure
          .put("ssz_static/Eth1Data", new SszTestExecutor<>(__ -> Eth1Data.SSZ_SCHEMA))
          .put("ssz_static/Fork", new SszTestExecutor<>(__ -> Fork.SSZ_SCHEMA))
          .put("ssz_static/ForkData", new SszTestExecutor<>(__ -> ForkData.SSZ_SCHEMA))
          .put(
              "ssz_static/ProposerSlashing",
              new SszTestExecutor<>(__ -> ProposerSlashing.SSZ_SCHEMA))
          .put(
              "ssz_static/SignedBeaconBlockHeader",
              new SszTestExecutor<>(__ -> SignedBeaconBlockHeader.SSZ_SCHEMA))
          .put(
              "ssz_static/SignedVoluntaryExit",
              new SszTestExecutor<>(__ -> SignedVoluntaryExit.SSZ_SCHEMA))
          .put("ssz_static/SigningData", new SszTestExecutor<>(__ -> SigningData.SSZ_SCHEMA))
          .put("ssz_static/Validator", new SszTestExecutor<>(__ -> Validator.SSZ_SCHEMA))
          .put("ssz_static/VoluntaryExit", new SszTestExecutor<>(__ -> VoluntaryExit.SSZ_SCHEMA))
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
    final SszSchema<T> sszSchema = sszType.get(schemaDefinitions);
    final T result = sszSchema.sszDeserialize(inputData);

    // Deserialize
    assertThat(result.hashTreeRoot()).isEqualTo(expectedRoot);

    // Serialize
    assertThat(result.sszSerialize()).isEqualTo(inputData);

    // Check TypeDefinition by parsing from YAML version
    final T yamlResult =
        TestDataUtils.loadYaml(testDefinition, "value.yaml", sszSchema.getJsonTypeDefinition());
    assertThat(yamlResult.hashTreeRoot()).isEqualTo(expectedRoot);
  }

  private interface SchemaProvider<T extends SszData> {
    SszSchema<T> get(SchemaDefinitions schemas);
  }
}
