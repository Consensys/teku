/*
 * Copyright Consensys Software Inc., 2024
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodySchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChangeSchema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

public class SchemaTypes {
  public static final SchemaId<SszBitvectorSchema<SszBitvector>> ATTNETS_ENR_FIELD_SCHEMA =
      create("ATTNETS_ENR_FIELD_SCHEMA");
  public static final SchemaId<SszBitvectorSchema<SszBitvector>> SYNCNETS_ENR_FIELD_SCHEMA =
      create("SYNCNETS_ENR_FIELD_SCHEMA");
  public static final SchemaId<HistoricalBatchSchema> HISTORICAL_BATCH_SCHEMA =
      create("HISTORICAL_BATCH_SCHEMA");
  public static final SchemaId<BeaconBlocksByRootRequestMessageSchema>
      BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA =
          create("BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA");

  public static final SchemaId<BeaconBlockBodySchema<? extends BeaconBlockBody>>
      BEACON_BLOCK_BODY_SCHEMA = create("BEACON_BLOCK_BODY_SCHEMA");
  public static final SchemaId<BeaconBlockSchema> BEACON_BLOCK_SCHEMA =
      create("BEACON_BLOCK_SCHEMA");
  public static final SchemaId<SignedBeaconBlockSchema> SIGNED_BEACON_BLOCK_SCHEMA =
      create("SIGNED_BEACON_BLOCK_SCHEMA");
  public static final SchemaId<
          BeaconStateSchema<? extends BeaconState, ? extends MutableBeaconState>>
      BEACON_STATE_SCHEMA = create("BEACON_STATE_SCHEMA");
  public static final SchemaId<AttestationSchema<Attestation>> ATTESTATION_SCHEMA =
      create("ATTESTATION_SCHEMA");
  public static final SchemaId<SignedAggregateAndProofSchema> SIGNED_AGGREGATE_AND_PROOF_SCHEMA =
      create("SIGNED_AGGREGATE_AND_PROOF_SCHEMA");
  public static final SchemaId<AggregateAndProofSchema> AGGREGATE_AND_PROOF_SCHEMA =
      create("AGGREGATE_AND_PROOF_SCHEMA");
  public static final SchemaId<MetadataMessageSchema<? extends MetadataMessage>>
      METADATA_MESSAGE_SCHEMA = create("METADATA_MESSAGE_SCHEMA");
  public static final SchemaId<AttesterSlashingSchema<AttesterSlashing>> ATTESTER_SLASHING_SCHEMA =
      create("ATTESTER_SLASHING_SCHEMA");
  public static final SchemaId<IndexedAttestationSchema<IndexedAttestation>>
      INDEXED_ATTESTATION_SCHEMA = create("INDEXED_ATTESTATION_SCHEMA");

  // Bellatrix
  public static final SchemaId<ExecutionPayloadSchema<? extends ExecutionPayload>>
      EXECUTION_PAYLOAD_SCHEMA = create("EXECUTION_PAYLOAD_SCHEMA");
  public static final SchemaId<ExecutionPayloadHeaderSchema<? extends ExecutionPayloadHeader>>
      EXECUTION_PAYLOAD_HEADER_SCHEMA = create("EXECUTION_PAYLOAD_HEADER_SCHEMA");
  public static final SchemaId<BlindedBeaconBlockBodySchemaBellatrix<?>>
      BLINDED_BEACON_BLOCK_BODY_SCHEMA = create("BLINDED_BEACON_BLOCK_BODY_SCHEMA");
  public static final SchemaId<BeaconBlockSchema> BLINDED_BEACON_BLOCK_SCHEMA =
      create("BLINDED_BEACON_BLOCK_SCHEMA");
  public static final SchemaId<SignedBeaconBlockSchema> SIGNED_BLINDED_BEACON_BLOCK_SCHEMA =
      create("SIGNED_BLINDED_BEACON_BLOCK_SCHEMA");

  // Capella
  public static final SchemaId<SignedBlsToExecutionChangeSchema>
      SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA = create("SIGNED_BLS_TO_EXECUTION_CHANGE_SCHEMA");
  public static final SchemaId<BlsToExecutionChangeSchema> BLS_TO_EXECUTION_CHANGE_SCHEMA =
      create("BLS_TO_EXECUTION_CHANGE_SCHEMA");

  // Deneb
  public static final SchemaId<BlobKzgCommitmentsSchema> BLOB_KZG_COMMITMENTS_SCHEMA =
      create("BLOB_KZG_COMMITMENTS_SCHEMA");

  private SchemaTypes() {
    // Prevent instantiation
  }

  @VisibleForTesting
  static <T> SchemaId<T> create(final String name) {
    return new SchemaId<>(name);
  }

  public static class SchemaId<T> {
    private final String name;

    private SchemaId(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof SchemaId<?> other) {
        return name.equals(other.name);
      }
      return false;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("name", name).toString();
    }
  }
}
