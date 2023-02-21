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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
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

  ForkDigestPayloadContext<BlobsSidecar> BLOBS_SIDECAR =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final BlobsSidecar responsePayload) {
          return responsePayload.getBeaconBlockSlot();
        }

        @Override
        public SszSchema<BlobsSidecar> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return schemaDefinitions.toVersionDeneb().orElseThrow().getBlobsSidecarSchema();
        }
      };

  ForkDigestPayloadContext<SignedBlobSidecar> SIGNED_BLOB_SIDECAR =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final SignedBlobSidecar responsePayload) {
          return responsePayload.getBlobSidecar().getSlot();
        }

        @Override
        public SszSchema<SignedBlobSidecar> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return schemaDefinitions.toVersionDeneb().orElseThrow().getSignedBlobSidecarSchema();
        }
      };

  ForkDigestPayloadContext<SignedBeaconBlockAndBlobsSidecar> SIGNED_BEACON_BLOCK_AND_BLOBS_SIDECAR =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final SignedBeaconBlockAndBlobsSidecar responsePayload) {
          return responsePayload.getSignedBeaconBlock().getSlot();
        }

        @Override
        public SszSchema<SignedBeaconBlockAndBlobsSidecar> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return schemaDefinitions
              .toVersionDeneb()
              .orElseThrow()
              .getSignedBeaconBlockAndBlobsSidecarSchema();
        }
      };

  UInt64 getSlotFromPayload(final TPayload responsePayload);

  SszSchema<TPayload> getSchemaFromSchemaDefinitions(final SchemaDefinitions schemaDefinitions);
}
