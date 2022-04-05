/*
 * Copyright 2022 ConsenSys AG.
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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class FinalizedCheckpointEvent
    extends Event<FinalizedCheckpointEvent.FinalizedCheckpointData> {

  static final SerializableTypeDefinition<FinalizedCheckpointData> FINALIZED_CHECKPOINT_EVENT_TYPE =
      SerializableTypeDefinition.object(FinalizedCheckpointData.class)
          .name("FinalizedCheckpointEvent")
          .withField("block", BYTES32_TYPE, FinalizedCheckpointData::getBlock)
          .withField("state", BYTES32_TYPE, FinalizedCheckpointData::getState)
          .withField("epoch", UINT64_TYPE, FinalizedCheckpointData::getEpoch)
          // TODO #5264
          // .withOptionalField("execution_optimistic", BOOLEAN_TYPE,
          // FinalizedCheckpointData::getExecutionOptimistic)
          .build();

  FinalizedCheckpointEvent(
      final Bytes32 block,
      final Bytes32 state,
      final UInt64 epoch,
      final Boolean executionOptimistic) {
    super(
        FINALIZED_CHECKPOINT_EVENT_TYPE,
        new FinalizedCheckpointData(block, state, epoch, Optional.ofNullable(executionOptimistic)));
  }

  public static class FinalizedCheckpointData {
    public final Bytes32 block;
    public final Bytes32 state;
    public final UInt64 epoch;
    public final Optional<Boolean> executionOptimistic;

    FinalizedCheckpointData(
        final Bytes32 block,
        final Bytes32 state,
        final UInt64 epoch,
        final Optional<Boolean> executionOptimistic) {
      this.block = block;
      this.state = state;
      this.epoch = epoch;
      this.executionOptimistic = executionOptimistic;
    }

    private Bytes32 getBlock() {
      return block;
    }

    private Bytes32 getState() {
      return state;
    }

    private UInt64 getEpoch() {
      return epoch;
    }

    @SuppressWarnings("UnusedMethod")
    private Optional<Boolean> getExecutionOptimistic() {
      return executionOptimistic;
    }
  }
}
