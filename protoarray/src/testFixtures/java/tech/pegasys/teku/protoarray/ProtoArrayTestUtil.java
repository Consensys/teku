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

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.int_to_bytes32;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.client.Store;

public class ProtoArrayTestUtil {

  // Gives a deterministic hash for a given integer
  public static Bytes32 getHash(int i) {
    return int_to_bytes32(Integer.toUnsignedLong(i + 1));
  }

  public static ProtoArrayForkChoiceStrategy createProtoArrayForkChoiceStrategy(
      Bytes32 finalizedBlockRoot,
      UnsignedLong finalizedBlockSlot,
      UnsignedLong finalizedCheckpointEpoch,
      UnsignedLong justifiedCheckpointEpoch) {
    Store store =
        Store.create(
            UnsignedLong.ONE,
            ZERO,
            new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO),
            new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO),
            new Checkpoint(ONE, Bytes32.ZERO),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>());

    ProtoArrayForkChoiceStrategy forkChoice = ProtoArrayForkChoiceStrategy.create(store);

    forkChoice.processBlock(
        finalizedBlockSlot,
        finalizedBlockRoot,
        Bytes32.ZERO,
        Bytes32.ZERO,
        justifiedCheckpointEpoch,
        finalizedCheckpointEpoch);

    return forkChoice;
  }

  public static Store createStoreToManipulateVotes() {
    return Store.create(
        UnsignedLong.ONE,
        ZERO,
        new Checkpoint(ZERO, Bytes32.ZERO),
        new Checkpoint(ZERO, Bytes32.ZERO),
        new Checkpoint(ONE, Bytes32.ZERO),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>());
  }
}
