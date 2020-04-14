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

package tech.pegasys.artemis.datastructures.forkchoice;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public interface MutableStore extends ReadOnlyStore {

  void putCheckpointState(Checkpoint checkpoint, BeaconState state);

  void putBlockState(Bytes32 blockRoot, BeaconState state);

  void putBlock(Bytes32 blockRoot, SignedBeaconBlock block);

  void setTime(UnsignedLong time);

  void setGenesis_time(UnsignedLong genesis_time);

  void setJustifiedCheckpoint(Checkpoint justified_checkpoint);

  void setFinalizedCheckpoint(Checkpoint finalized_checkpoint);

  void setBestJustifiedCheckpoint(Checkpoint best_justified_checkpoint);
}
