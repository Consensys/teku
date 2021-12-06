/*
 * Copyright 2021 ConsenSys AG.
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
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public interface ForkDigestPayloadContext<TPayload extends SszData> {

  ForkDigestPayloadContext<SignedBeaconBlock> SIGNED_BEACONBLOCK =
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

  UInt64 getSlotFromPayload(final TPayload responsePayload);

  SszSchema<TPayload> getSchemaFromSchemaDefinitions(final SchemaDefinitions schemaDefinitions);
}
