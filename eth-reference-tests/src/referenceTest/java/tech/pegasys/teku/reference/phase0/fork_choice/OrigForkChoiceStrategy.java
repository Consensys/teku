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

package tech.pegasys.teku.reference.phase0.fork_choice;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.teku.util.config.Constants.GENESIS_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.util.config.Constants;

public class OrigForkChoiceStrategy implements ForkChoiceStrategy {
  final Map<UnsignedLong, Checkpoint> latestMessages = new HashMap<>();
  final Map<Bytes32, BeaconBlock> blocks = new HashMap<>();

  public OrigForkChoiceStrategy(UpdatableStore store) {
    CheckpointAndBlock finalizedCheckpointAndBlock = store.getFinalizedCheckpointAndBlock();
    BeaconBlock genesisBlock = finalizedCheckpointAndBlock.getBlock().getMessage();
    if (!genesisBlock.getSlot().equals(UnsignedLong.valueOf(Constants.GENESIS_SLOT))) {
      throw new IllegalArgumentException();
    }
    onBlock(genesisBlock, store.getBlockState(genesisBlock.hash_tree_root()));
  }

  public static Bytes32 get_ancestor(ReadOnlyStore store, Bytes32 root, UnsignedLong slot) {
    BeaconBlock block = store.getBlock(root);
    if (block.getSlot().compareTo(slot) > 0) {
      return get_ancestor(store, block.getParent_root(), slot);
    } else if (block.getSlot().equals(slot)) {
      return root;
    } else {
      // root is older than the queried slot, thus a skip slot. Return earliest root prior to slot.
      return root;
    }
  }

  public UnsignedLong get_latest_attesting_balance(ReadOnlyStore store, Bytes32 root) {
    BeaconState state = store.getCheckpointState(store.getJustifiedCheckpoint());
    List<Integer> active_indices = get_active_validator_indices(state, get_current_epoch(state));
    return active_indices.stream()
        .filter(
            i ->
                latestMessages.containsKey(UnsignedLong.valueOf(i))
                    && get_ancestor(
                            store,
                            latestMessages.get(UnsignedLong.valueOf(i)).getRoot(),
                            store.getBlock(root).getSlot())
                        .equals(root))
        .map(i -> state.getValidators().get(i).getEffective_balance())
        .reduce(UnsignedLong.ZERO, UnsignedLong::plus);
  }

  public static boolean filter_block_tree(
      ReadOnlyStore store, Bytes32 block_root, Map<Bytes32, BeaconBlock> blocks) {
    BeaconBlock block = store.getBlock(block_root);
    List<Bytes32> children =
        store.getBlockRoots().stream()
            .filter(root -> store.getBlock(root).getParent_root().equals(block_root))
            .collect(Collectors.toList());
    // If any children branches contain expected finalized/justified checkpoints,
    // add to filtered block-tree and signal viability to parent
    if (!children.isEmpty()) {
      boolean filter_block_tree_result =
          children.stream()
              .map(child -> filter_block_tree(store, child, blocks))
              .reduce((a, b) -> a || b)
              .orElse(false);
      if (filter_block_tree_result) {
        blocks.put(block_root, block);
        return true;
      }
      return false;
    }

    BeaconState head_state = store.getBlockState(block_root);
    boolean correct_justified =
        store.getJustifiedCheckpoint().getEpoch().equals(UnsignedLong.valueOf(GENESIS_EPOCH))
            || head_state.getCurrent_justified_checkpoint().equals(store.getJustifiedCheckpoint());
    boolean correct_finalized =
        store.getFinalizedCheckpoint().getEpoch().equals(UnsignedLong.valueOf(GENESIS_EPOCH))
            || head_state.getFinalized_checkpoint().equals(store.getFinalizedCheckpoint());

    if (correct_justified && correct_finalized) {
      blocks.put(block_root, block);
      return true;
    }
    return false;
  }

  /**
   * Retrieve a filtered block tree from store, only returning branches whose leaf state's
   * justified/finalized info agrees with that in store.
   *
   * @param store
   * @return
   */
  public static Map<Bytes32, BeaconBlock> get_filtered_block_tree(ReadOnlyStore store) {
    Bytes32 base = store.getJustifiedCheckpoint().getRoot();
    Map<Bytes32, BeaconBlock> blocks = new HashMap<>();
    filter_block_tree(store, base, blocks);
    return blocks;
  }

  @Override
  public Bytes32 findHead(MutableStore store) {
    // Get filtered block tree that only includes viable branches
    final Map<Bytes32, BeaconBlock> blocks = get_filtered_block_tree(store);

    // Execute the LMD-GHOST fork choice
    Bytes32 head = store.getJustifiedCheckpoint().getRoot();
    UnsignedLong justified_slot = store.getJustifiedCheckpoint().getEpochStartSlot();

    while (true) {
      final Bytes32 head_in_filter = head;
      List<Bytes32> children =
          blocks.entrySet().stream()
              .filter(
                  (entry) -> {
                    final BeaconBlock block = entry.getValue();
                    return block.getParent_root().equals(head_in_filter)
                        && block.getSlot().compareTo(justified_slot) > 0;
                  })
              .map(Map.Entry::getKey)
              .collect(Collectors.toList());

      if (children.size() == 0) {
        return head;
      }

      // Sort by latest attesting balance with ties broken lexicographically
      UnsignedLong max_value = UnsignedLong.ZERO;
      for (Bytes32 child : children) {
        UnsignedLong curr_value = get_latest_attesting_balance(store, child);
        if (curr_value.compareTo(max_value) > 0) {
          max_value = curr_value;
        }
      }

      final UnsignedLong max = max_value;
      head =
          children.stream()
              .filter(child -> get_latest_attesting_balance(store, child).compareTo(max) == 0)
              .max(Comparator.comparing(Bytes::toHexString))
              .get();
    }
  }

  @Override
  public void onAttestation(final MutableStore store, IndexedAttestation attestation) {
    Checkpoint target = attestation.getData().getTarget();
    // Update latest messages
    for (UnsignedLong i : attestation.getAttesting_indices()) {
      if (!latestMessages.containsKey(i)
          || target.getEpoch().compareTo(latestMessages.get(i).getEpoch()) > 0) {
        latestMessages.put(
            i, new Checkpoint(target.getEpoch(), attestation.getData().getBeacon_block_root()));
      }
    }
  }

  @Override
  public void onBlock(final BeaconBlock block, final BeaconState state) {
    blocks.put(block.hash_tree_root(), block);
  }

  @Override
  public Optional<UnsignedLong> blockSlot(Bytes32 blockRoot) {
    return Optional.ofNullable(blocks.get(blockRoot)).map(BeaconBlock::getSlot);
  }

  @Override
  public Optional<Bytes32> blockParentRoot(Bytes32 blockRoot) {
    return Optional.ofNullable(blocks.get(blockRoot)).map(BeaconBlock::getParent_root);
  }

  @Override
  public boolean contains(Bytes32 blockRoot) {
    return blocks.containsKey(blockRoot);
  }
}
