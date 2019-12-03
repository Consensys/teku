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

import com.google.common.primitives.UnsignedLong;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public interface ReadOnlyStore {

  UnsignedLong getTime();

  UnsignedLong getGenesisTime();

  Checkpoint getJustifiedCheckpoint();

  Checkpoint getFinalizedCheckpoint();

  Checkpoint getBestJustifiedCheckpoint();

  BeaconBlock getBlock(Bytes32 blockRoot);

  boolean containsBlock(Bytes32 blockRoot);

  Set<Bytes32> getBlockRoots();

  BeaconState getBlockState(Bytes32 blockRoot);

  boolean containsBlockState(Bytes32 blockRoot);

  BeaconState getCheckpointState(Checkpoint checkpoint);

  boolean containsCheckpointState(Checkpoint checkpoint);

  Checkpoint getLatestMessage(UnsignedLong validatorIndex);

  boolean containsLatestMessage(UnsignedLong validatorIndex);
}
