/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.schemas;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.operations.AttestationContainer;
import tech.pegasys.teku.spec.datastructures.operations.AttestationContainerSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;

public abstract class AbstractSchemaDefinitions implements SchemaDefinitions {

  final SszBitvectorSchema<SszBitvector> attnetsENRFieldSchema;
  final SszBitvectorSchema<SszBitvector> syncnetsENRFieldSchema =
      SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT);
  private final HistoricalBatchSchema historicalBatchSchema;
  private final SignedAggregateAndProofSchema signedAggregateAndProofSchema;
  private final IndexedAttestation.IndexedAttestationSchema indexedAttestationSchema;
  private final AttesterSlashing.AttesterSlashingSchema attesterSlashingSchema;
  private final BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema
      beaconBlocksByRootRequestMessageSchema;

  public AbstractSchemaDefinitions(final SpecConfig specConfig) {
    this.historicalBatchSchema = new HistoricalBatchSchema(specConfig.getSlotsPerHistoricalRoot());
    this.signedAggregateAndProofSchema = new SignedAggregateAndProofSchema(specConfig);
    this.indexedAttestationSchema =
        new IndexedAttestation.IndexedAttestationSchema(getMaxValidatorPerAttestation(specConfig));
    this.attesterSlashingSchema =
        new AttesterSlashing.AttesterSlashingSchema(indexedAttestationSchema);
    this.beaconBlocksByRootRequestMessageSchema =
        new BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema(specConfig);
    this.attnetsENRFieldSchema = SszBitvectorSchema.create(specConfig.getAttestationSubnetCount());
  }

  abstract long getMaxValidatorPerAttestation(SpecConfig specConfig);

  @Override
  public SszBitvectorSchema<SszBitvector> getAttnetsENRFieldSchema() {
    return attnetsENRFieldSchema;
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getSyncnetsENRFieldSchema() {
    return syncnetsENRFieldSchema;
  }

  @Override
  public HistoricalBatchSchema getHistoricalBatchSchema() {
    return historicalBatchSchema;
  }

  @Override
  public SignedAggregateAndProofSchema getSignedAggregateAndProofSchema() {
    return signedAggregateAndProofSchema;
  }

  @Override
  public AttestationContainerSchema<AttestationContainer> getAttestationContainerSchema() {
    return getAttestationSchema().castTypeToAttestationContainer();
  }

  @Override
  public IndexedAttestation.IndexedAttestationSchema getIndexedAttestationSchema() {
    return indexedAttestationSchema;
  }

  @Override
  public AttesterSlashing.AttesterSlashingSchema getAttesterSlashingSchema() {
    return attesterSlashingSchema;
  }

  @Override
  public BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema
      getBeaconBlocksByRootRequestMessageSchema() {
    return beaconBlocksByRootRequestMessageSchema;
  }
}
