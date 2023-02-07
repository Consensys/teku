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

package tech.pegasys.teku.spec.schemas;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;

public interface SchemaDefinitions {

  BeaconStateSchema<?, ?> getBeaconStateSchema();

  SignedBeaconBlockSchema getSignedBeaconBlockSchema();

  BeaconBlockSchema getBeaconBlockSchema();

  BeaconBlockBodySchema<?> getBeaconBlockBodySchema();

  BeaconBlockSchema getBlindedBeaconBlockSchema();

  BeaconBlockBodySchema<?> getBlindedBeaconBlockBodySchema();

  SignedBeaconBlockSchema getSignedBlindedBeaconBlockSchema();

  MetadataMessageSchema<?> getMetadataMessageSchema();

  SszBitvectorSchema<SszBitvector> getAttnetsENRFieldSchema();

  SszBitvectorSchema<SszBitvector> getSyncnetsENRFieldSchema();

  HistoricalBatchSchema getHistoricalBatchSchema();

  SignedAggregateAndProofSchema getSignedAggregateAndProofSchema();

  default AggregateAndProofSchema getAggregateAndProofSchema() {
    return getSignedAggregateAndProofSchema().getAggregateAndProofSchema();
  }

  default AttestationSchema getAttestationSchema() {
    return getAggregateAndProofSchema().getAttestationSchema();
  }

  IndexedAttestationSchema getIndexedAttestationSchema();

  AttesterSlashingSchema getAttesterSlashingSchema();

  @NonSchema
  default Optional<SchemaDefinitionsAltair> toVersionAltair() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsCapella> toVersionCapella() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsDeneb> toVersionDeneb() {
    return Optional.empty();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface NonSchema {}
}
