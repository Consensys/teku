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

package tech.pegasys.teku.datastructures.forkchoice;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class TestStoreFactory {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  public MutableStore createGenesisStore() {
    final BeaconState genesisState = createRandomGenesisState();
    return getForkChoiceStore(genesisState);
  }

  public MutableStore createGenesisStore(final BeaconState genesisState) {
    checkArgument(
        genesisState.getSlot().equals(UInt64.valueOf(Constants.GENESIS_SLOT)),
        "Genesis state has invalid slot.");
    return getForkChoiceStore(genesisState);
  }

  public MutableStore createEmptyStore() {
    return new TestStoreImpl(
        UInt64.ZERO,
        UInt64.ZERO,
        null,
        null,
        null,
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>());
  }

  private MutableStore getForkChoiceStore(final BeaconState anchorState) {
    final BeaconBlock anchorBlock = new BeaconBlock(anchorState.hash_tree_root());
    final SignedBeaconBlock signedAnchorBlock =
        new SignedBeaconBlock(anchorBlock, BLSSignature.empty());
    final Bytes32 anchorRoot = anchorBlock.hash_tree_root();
    final UInt64 anchorEpoch = BeaconStateUtil.get_current_epoch(anchorState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);

    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    Map<UInt64, VoteTracker> votes = new HashMap<>();

    blocks.put(anchorRoot, signedAnchorBlock);
    block_states.put(anchorRoot, anchorState);
    checkpoint_states.put(anchorCheckpoint, anchorState);

    return new TestStoreImpl(
        anchorState
            .getGenesis_time()
            .plus(UInt64.valueOf(SECONDS_PER_SLOT).times(anchorState.getSlot())),
        anchorState.getGenesis_time(),
        anchorCheckpoint,
        anchorCheckpoint,
        anchorCheckpoint,
        blocks,
        block_states,
        checkpoint_states,
        votes);
  }

  private BeaconState createRandomGenesisState() {
    return dataStructureUtil.randomBeaconState(UInt64.valueOf(Constants.GENESIS_SLOT));
  }
}
