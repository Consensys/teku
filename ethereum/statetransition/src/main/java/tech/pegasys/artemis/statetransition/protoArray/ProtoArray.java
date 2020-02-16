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

package tech.pegasys.artemis.statetransition.protoArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;

public class ProtoArray {

  private List<ProtoBlock> array = new ArrayList<>();
  private Map<Bytes32, Integer> blockRootToBlockIndex = new HashMap<>();
  private Map<Integer, Integer> validatorToLastVotedBlockIndex = new HashMap<>();
  private Map<Integer, Integer> validatorToEffectiveBalance = new HashMap<>();

  public synchronized void onBlock(SignedBeaconBlock signedBeaconBlock) {
    BeaconBlock beaconBlock = signedBeaconBlock.getMessage();
    Bytes32 blockRoot = beaconBlock.hash_tree_root();
    Bytes32 parentRoot = beaconBlock.getParent_root();

    // Check if block is viable
    Optional<ProtoBlock> maybeParentProtoBlock = getProtoBlock(parentRoot);
    ProtoBlock parentProtoBlock;
    if (maybeParentProtoBlock.isEmpty()) {
      // Parent block was not found in the array, i.e. the block is not in the filtered tree
      return;
    } else {
      parentProtoBlock = maybeParentProtoBlock.get();
    }

    // Append the latest block to array
    int blockIndex = array.size();
    ProtoBlock newProtoBlock = ProtoBlock.createNewProtoBlock(blockIndex, blockRoot, parentRoot);
    array.add(newProtoBlock);

    blockRootToBlockIndex.put(blockRoot, blockIndex);

    // If block’s parent has a best child already, return
    Optional<Integer> maybeParentsBestChildIndex = parentProtoBlock.getBestChildIndex();
    if (maybeParentsBestChildIndex.isPresent()) {
      return;
    }
    parentProtoBlock.setBestChildIndex(blockIndex);
    parentProtoBlock.setBestDescendantIndex(blockIndex);
  }

  private Optional<ProtoBlock> getProtoBlock(Bytes32 root) {
    return Optional.ofNullable(blockRootToBlockIndex.get(root)).map(index -> array.get(index));
  }
}
