package tech.pegasys.artemis.statetransition.protoArray;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProtoArray {

  private List<ProtoBlock> array = new ArrayList<>();
  private Map<Bytes32, Integer>  blockRootToBlockIndex = new HashMap<>();
  private Map<Integer, Integer>  validatorToLastVotedBlockIndex = new HashMap<>();
  private Map<Integer, Integer>  validatorToEffectiveBalance = new HashMap<>();

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
