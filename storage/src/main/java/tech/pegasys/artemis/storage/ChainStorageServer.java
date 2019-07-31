/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_of_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.util.alogger.ALogger;

/** This class is the ChainStorage server-side logic */
public class ChainStorageServer extends ChainStorageClient implements ChainStorage {
  static final ALogger LOG = new ALogger(ChainStorageServer.class.getName());

  private static DB db = DBMaker.fileDB("artemis.db").transactionEnable().make();

  // Store
  private static Atomic.Var<UnsignedLong> time =
      db.atomicVar("time", new UnsignedLongSerializer()).createOrOpen();
  private static Atomic.Var<Checkpoint> justifiedCheckpoint =
      db.atomicVar("justified_checkpoint", new Checkpoint.CheckpointSerializer()).createOrOpen();
  private static Atomic.Var<Checkpoint> finalizedCheckpoint =
      db.atomicVar("finalized_checkpoint", new Checkpoint.CheckpointSerializer()).createOrOpen();
  private static ConcurrentMap<Bytes32, BeaconBlock> blocks =
      db.hashMap("blocks_map", new Bytes32Serializer(), new BeaconBlock.BeaconBlockSerializer())
          .createOrOpen();
  private static ConcurrentMap<Bytes32, BeaconState> block_states =
      db.hashMap(
              "block_states_map", new Bytes32Serializer(), new BeaconState.BeaconStateSerializer())
          .createOrOpen();
  private static ConcurrentMap<Checkpoint, BeaconState> checkpoint_states =
      db.hashMap(
              "checkpoint_states_map",
              new Checkpoint.CheckpointSerializer(),
              new BeaconState.BeaconStateSerializer())
          .createOrOpen();
  private static ConcurrentMap<UnsignedLong, LatestMessage> latest_messages =
      db.hashMap(
              "latest_messages_map",
              new UnsignedLongSerializer(),
              new LatestMessage.LatestMessageSerializer())
          .createOrOpen();

  public static void onNewProcessedBlock(ProcessedBlockEvent processedBlockEvent) {
    BeaconStateWithCache postState = processedBlockEvent.getPostState();
    BeaconBlock processedBlock = processedBlockEvent.getProcessedBlock();
    Checkpoint newJustifiedCheckpoint = processedBlockEvent.getJustifiedCheckpoint();
    Checkpoint newFinalizedCheckpoint = processedBlockEvent.getFinalizedCheckpoint();

    Bytes32 blockRoot = processedBlock.signing_root("signature");
    block_states.put(blockRoot, BeaconStateWithCache.deepCopy(postState));
    blocks.put(blockRoot, processedBlock);
    justifiedCheckpoint.set(newJustifiedCheckpoint);
    finalizedCheckpoint.set(newFinalizedCheckpoint);
    db.commit();
  }

  public static void onNewAttestation(NewAttestationEvent newAttestationEvent) {
    Checkpoint checkpoint = newAttestationEvent.getCheckpoint();
    BeaconStateWithCache state = newAttestationEvent.getState();
    List<Pair<UnsignedLong, LatestMessage>> attesterLatestMessages =
        newAttestationEvent.getAttesterLatestsMessages();

    attesterLatestMessages.forEach(pair -> latest_messages.put(pair.getLeft(), pair.getRight()));

    if (checkpoint != null) {
      checkpoint_states.put(checkpoint, state);
    }
    db.commit();
  }

  public static void onNewSlot(SlotEvent slotEvent) {
    if (compute_start_slot_of_epoch(compute_epoch_of_slot(slotEvent.getSlot()))
        .equals(slotEvent.getSlot())) {
      time.set(slotEvent.getTime());
      // printDB();
    }
    db.commit();
  }

  @SuppressWarnings("ObjectToString")
  private static void printDB() {
    System.out.println("justified checkpoint: " + justifiedCheckpoint.get());
    System.out.println("finalized checkpoint: " + finalizedCheckpoint.get());
    blocks.values().forEach(block -> System.out.println("block: " + block.toString()));
    block_states.values().forEach(state -> System.out.println("blockState: " + state.toString()));
    checkpoint_states
        .values()
        .forEach(checkpoint -> System.out.println("checkpointState: " + checkpoint.toString()));
    latest_messages
        .values()
        .forEach(
            latestMessage ->
                System.out.println("attesterLatestMessage: " + latestMessage.toString()));
  }
}
