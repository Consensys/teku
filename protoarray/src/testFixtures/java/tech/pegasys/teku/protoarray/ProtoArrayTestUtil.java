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

package tech.pegasys.teku.protoarray;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.int_to_bytes32;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class ProtoArrayTestUtil {
  private static final TestStoreFactory STORE_FACTORY = new TestStoreFactory();

  // Gives a deterministic hash for a given integer
  public static Bytes32 getHash(int i) {
    return int_to_bytes32(Integer.toUnsignedLong(i + 1));
  }

  public static ProtoArrayForkChoiceStrategy createProtoArrayForkChoiceStrategy(
      Bytes32 finalizedBlockRoot,
      UnsignedLong finalizedBlockSlot,
      UnsignedLong finalizedCheckpointEpoch,
      UnsignedLong justifiedCheckpointEpoch) {
    MutableStore store = STORE_FACTORY.createEmptyStore();
    store.setJustifiedCheckpoint(new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO));
    store.setFinalizedCheckpoint(new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO));

    ProtoArrayForkChoiceStrategy forkChoice =
        ProtoArrayForkChoiceStrategy.create(
            new HashMap<>(), store.getFinalizedCheckpoint(), store.getJustifiedCheckpoint());

    ProtoArrayForkChoiceStrategyUpdater updater = forkChoice.updater();
    updater.processBlock(
        finalizedBlockSlot,
        finalizedBlockRoot,
        Bytes32.ZERO,
        Bytes32.ZERO,
        justifiedCheckpointEpoch,
        finalizedCheckpointEpoch);
    updater.commit();

    return forkChoice;
  }

  public static MutableStore createStoreToManipulateVotes() {
    return STORE_FACTORY.createMutableGenesisStore();
  }
}
