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

import com.google.common.primitives.UnsignedLong;
import org.apache.commons.lang3.builder.Diff;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Validator;

import static tech.pegasys.artemis.datastructures.util.AttestationUtil.getParticipantIndices;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_total_balance;
import static tech.pegasys.artemis.statetransition.protoArray.DiffArray.createDiffArray;

public class ProtoArray  {

  private List<ProtoBlock> array = new ArrayList<>();
  private Map<Bytes32, Integer> blockRootToIndex = new HashMap<>();
  private Map<Integer, Integer> validatorToLastVotedBlockIndex = new HashMap<>();
  private Map<Integer, UnsignedLong> validatorToEffectiveBalance = new HashMap<>();
  private int finalizedBlockIndex = 0;

  public ProtoArray(BeaconState state) {
    updateValidatorsEffectiveBalances(state);
  }

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

    blockRootToIndex.put(blockRoot, blockIndex);

    // If block’s parent has a best child already, return
    Optional<Integer> maybeParentsBestChildIndex = parentProtoBlock.getBestChildIndex();
    if (maybeParentsBestChildIndex.isPresent()) {
      return;
    }
    parentProtoBlock.setBestChildIndex(blockIndex);
    parentProtoBlock.setBestDescendantIndex(blockIndex);
  }

  public synchronized void onAttestations(BeaconState state, Attestation attestation) {
    DiffArray diffArray = createDiffArray(finalizedBlockIndex, array.size());

    if (getBlockIndex(attestation.getData().getBeacon_block_root()).isEmpty()) {
      return;
    }

    int blockIndex = getBlockIndex(attestation.getData().getBeacon_block_root()).get();

    List<Integer> participants = getParticipantIndices(state, attestation);

    participants
            .parallelStream()
            .forEach(participantIndex -> {
              clearParticipantsPreviousVoteWeights(diffArray, participantIndex);
              updateLatestBlockVoteForValidator(participantIndex, blockIndex);
            });

    long totalWeightOfAttestation = get_total_balance(state, participants).longValue();
    diffArray.noteChange(blockIndex, totalWeightOfAttestation);
    applyDiffArray(diffArray);
  }

  public void onJustifiedCheckpoint(BeaconState state, Checkpoint justifiedCheckpoint) {
    DiffArray diffArray = createDiffArray(finalizedBlockIndex, array.size());
  }

  private void applyDiffArray(DiffArray diffArray) {
    for (int blockIndex = finalizedBlockIndex + array.size() - 1;
         blockIndex >= 0;
         blockIndex--) {
      long weightDiff = diffArray.get(blockIndex);
      if (weightDiff == 0L) {
        continue;
      }

      ProtoBlock protoBlock = this.get(blockIndex);
      protoBlock.modifyWeight(weightDiff);

      getParentBlock(protoBlock).ifPresent(parentBlock -> {
        maybeUpdateBestChild(protoBlock, parentBlock);
        parentBlock.modifyWeight(weightDiff);
      });
    }
  }

  private Optional<ProtoBlock> getParentBlock(ProtoBlock childBlock) {
    // Check if parent block is still in the array
    Integer parentBlockIndex = blockRootToIndex.get(childBlock.getParentRoot());
    if (parentBlockIndex == null) {
      return Optional.empty();
    }

    return Optional.of(this.get(parentBlockIndex));
  }

  private void maybeUpdateBestChild(ProtoBlock protoBlock, ProtoBlock parentBlock) {
    int blockIndex = blockRootToIndex.get(protoBlock.getRoot());
    Integer bestChildIndex = parentBlock.getBestChildIndex().orElse(-1);
    if (bestChildIndex == blockIndex) {
      return;
    }

    // If the parent block does not have a best child yet or the best child has lower
    // weight than the current block:
    // - set parent's best child to current block
    // - set parent's best descendant to current block's best descendant
    if (bestChildIndex == -1 ||
            protoBlock.getWeight().compareTo(this.get(bestChildIndex).getWeight()) > 0) {
      parentBlock.setBestChildIndex(blockIndex);
      parentBlock.setBestDescendantIndex(protoBlock.getBestDescendantIndex());
    }
  }

  private ProtoBlock get(int blockIndex) {
    return array.get(blockIndex - finalizedBlockIndex);
  }

  private void set(int blockIndex, ProtoBlock block) {
    array.set(blockIndex - finalizedBlockIndex, block);
  }

  private Optional<ProtoBlock> getProtoBlock(Bytes32 root) {
    return Optional.ofNullable(blockRootToIndex.get(root)).map(index -> array.get(index));
  }

  private void updateLatestBlockVoteForValidator(int validatorIndex, int blockIndex) {
    validatorToLastVotedBlockIndex.put(validatorIndex, blockIndex);
  }

  private Optional<Integer> getBlockIndex(Bytes32 blockRoot) {
    return Optional.ofNullable(blockRootToIndex.get(blockRoot));
  }

  private Optional<Integer> getParentIndex(Bytes32 blockRoot) {
    return Optional.ofNullable(blockRootToIndex.get(blockRoot));
  }

  private void clearParticipantsPreviousVoteWeights(
          DiffArray diffArray,
          int validatorIndex) {
    int latestVotedBlockIndex = validatorToLastVotedBlockIndex.get(validatorIndex);
    long effectiveBalance = validatorToEffectiveBalance.get(validatorIndex).longValue();
    diffArray.noteChange(latestVotedBlockIndex, -effectiveBalance);
  }

  private void updateValidatorsEffectiveBalances(BeaconState newState) {
    List<Validator> validatorList = newState.getValidators();
    for (int validatorIndex = 0;
         validatorIndex < validatorList.size();
         validatorIndex++) {
      validatorToEffectiveBalance.put(
              validatorIndex,
              validatorList.get(validatorIndex).getEffective_balance());
    }
  }
}
