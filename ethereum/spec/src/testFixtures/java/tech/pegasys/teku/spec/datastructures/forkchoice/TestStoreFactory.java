/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class TestStoreFactory {
  private final Spec spec;
  private final DataStructureUtil dataStructureUtil;

  public TestStoreFactory() {
    this(TestSpecFactory.createDefault());
  }

  public TestStoreFactory(final Spec spec) {
    this.spec = spec;
    this.dataStructureUtil = new DataStructureUtil(spec);
  }

  public TestStoreImpl createGenesisStore() {
    return getForkChoiceStore(createAnchorForGenesis());
  }

  public TestStoreImpl createAnchorStore(final AnchorPoint anchor) {
    return getForkChoiceStore(anchor);
  }

  public TestStoreImpl createGenesisStore(final BeaconState genesisState) {
    checkArgument(
        genesisState.getSlot().equals(SpecConfig.GENESIS_SLOT), "Genesis state has invalid slot.");
    return getForkChoiceStore(createAnchorFromState(genesisState));
  }

  public TestStoreImpl createEmptyStore() {
    return new TestStoreImpl(
        spec,
        UInt64.ZERO,
        UInt64.ZERO,
        Optional.empty(),
        null,
        null,
        null,
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>());
  }

  private AnchorPoint createAnchorForGenesis() {
    return createAnchorFromState(createRandomGenesisState());
  }

  private AnchorPoint createAnchorFromState(final BeaconState anchorState) {
    return dataStructureUtil.createAnchorFromState(anchorState);
  }

  private TestStoreImpl getForkChoiceStore(final AnchorPoint anchor) {
    final BeaconState anchorState = anchor.getState();
    final Bytes32 anchorRoot = anchor.getRoot();
    final Checkpoint anchorCheckpoint = anchor.getCheckpoint();

    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BlobsSidecar> blobsSidecars = new HashMap<>();
    Map<Bytes32, BlockCheckpoints> blockCheckpoints = new HashMap<>();
    Map<Bytes32, BeaconState> blockStates = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpointStates = new HashMap<>();
    Map<UInt64, VoteTracker> votes = new HashMap<>();

    blocks.put(anchorRoot, anchor.getSignedBeaconBlock().orElseThrow());
    blockStates.put(anchorRoot, anchorState);
    blockCheckpoints.put(
        anchorRoot,
        new BlockCheckpoints(
            anchorCheckpoint, anchorCheckpoint, anchorCheckpoint, anchorCheckpoint));
    checkpointStates.put(anchorCheckpoint, anchorState);

    return new TestStoreImpl(
        spec,
        spec.getSlotStartTime(anchorState.getSlot(), anchorState.getGenesisTime()),
        anchorState.getGenesisTime(),
        Optional.of(anchorCheckpoint),
        anchorCheckpoint,
        anchorCheckpoint,
        anchorCheckpoint,
        blocks,
        blockStates,
        blobsSidecars,
        blockCheckpoints,
        checkpointStates,
        votes);
  }

  private BeaconState createRandomGenesisState() {
    return dataStructureUtil.randomBeaconState(SpecConfig.GENESIS_SLOT);
  }
}
