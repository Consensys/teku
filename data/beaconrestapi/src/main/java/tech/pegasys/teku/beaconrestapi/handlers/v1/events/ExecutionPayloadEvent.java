/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.ExecutionPayloadEvent.ExecutionPayloadData;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;

public class ExecutionPayloadEvent extends Event<ExecutionPayloadData> {

  public static final SerializableTypeDefinition<ExecutionPayloadData>
      EXECUTION_PAYLOAD_EVENT_TYPE =
          SerializableTypeDefinition.object(ExecutionPayloadData.class)
              .name("ExecutionPayloadEvent")
              .withField("slot", UINT64_TYPE, ExecutionPayloadData::getSlot)
              .withField("block_root", BYTES32_TYPE, ExecutionPayloadData::getBlockRoot)
              .withField(
                  "execution_block_hash", BYTES32_TYPE, ExecutionPayloadData::getExecutionBlockHash)
              .withField(
                  "execution_optimistic", BOOLEAN_TYPE, ExecutionPayloadData::isExecutionOptimistic)
              .build();

  ExecutionPayloadEvent(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    super(
        EXECUTION_PAYLOAD_EVENT_TYPE,
        new ExecutionPayloadData(
            executionPayload.getMessage().getSlot(),
            executionPayload.getMessage().getBeaconBlockRoot(),
            executionPayload.getMessage().getPayload().getBlockHash(),
            executionOptimistic));
  }

  public static class ExecutionPayloadData {
    private final UInt64 slot;
    private final Bytes32 blockRoot;
    private final Bytes32 executionBlockHash;
    private final boolean executionOptimistic;

    ExecutionPayloadData(
        final UInt64 slot,
        final Bytes32 blockRoot,
        final Bytes32 executionBlockHash,
        final boolean executionOptimistic) {
      this.slot = slot;
      this.blockRoot = blockRoot;
      this.executionBlockHash = executionBlockHash;
      this.executionOptimistic = executionOptimistic;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public Bytes32 getBlockRoot() {
      return blockRoot;
    }

    public Bytes32 getExecutionBlockHash() {
      return executionBlockHash;
    }

    public boolean isExecutionOptimistic() {
      return executionOptimistic;
    }
  }
}
