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

package tech.pegasys.teku.datastructures.state;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;

public class CheckpointAndBlock {
  final Checkpoint checkpoint;
  final SignedBeaconBlock block;

  public CheckpointAndBlock(final Checkpoint checkpoint, final SignedBeaconBlock block) {
    checkArgument(
        Objects.equals(checkpoint.getRoot(), block.getRoot()),
        "Block must belong to the Checkpoint");
    this.checkpoint = checkpoint;
    this.block = block;
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public Bytes32 getRoot() {
    return block.getRoot();
  }

  public UnsignedLong getBlockSlot() {
    return block.getSlot();
  }

  public UnsignedLong getEpoch() {
    return checkpoint.getEpoch();
  }

  public UnsignedLong getEpochStartSlot() {
    return checkpoint.getEpochStartSlot();
  }
}
