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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public interface ForkDigestPayloadContext<TPayload extends SszData> {

  ForkDigestPayloadContext<SignedBeaconBlock> SIGNED_BEACON_BLOCK =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final SignedBeaconBlock responsePayload) {
          return responsePayload.getMessage().getSlot();
        }

        @Override
        public SszSchema<SignedBeaconBlock> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return schemaDefinitions.getSignedBeaconBlockSchema();
        }
      };

  ForkDigestPayloadContext<BlobSidecar> BLOB_SIDECAR =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final BlobSidecar responsePayload) {
          return responsePayload.getSlot();
        }

        @Override
        public SszSchema<BlobSidecar> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return schemaDefinitions.toVersionDeneb().orElseThrow().getBlobSidecarSchema();
        }
      };

  static ForkDigestPayloadContext<SignedExecutionPayloadEnvelope> executionPayloadEnvelope(
      final Spec spec) {
    return new ForkDigestPayloadContext<>() {
      @Override
      public UInt64 getSlotFromPayload(final SignedExecutionPayloadEnvelope responsePayload) {
        // used only for the fork digest, so no need to be accurate (there is no slot field in the
        // ExecutionPayload)
        final UInt64 epoch =
            spec.getForkSchedule()
                .getFork(responsePayload.getMessage().getPayload().getMilestone())
                .getEpoch();
        return spec.computeStartSlotAtEpoch(epoch);
      }

      @Override
      public SszSchema<SignedExecutionPayloadEnvelope> getSchemaFromSchemaDefinitions(
          final SchemaDefinitions schemaDefinitions) {
        return schemaDefinitions
            .toVersionEip7732()
            .orElseThrow()
            .getSignedExecutionPayloadEnvelopeSchema();
      }
    };
  }

  UInt64 getSlotFromPayload(final TPayload responsePayload);

  SszSchema<TPayload> getSchemaFromSchemaDefinitions(final SchemaDefinitions schemaDefinitions);
}
