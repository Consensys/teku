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

package tech.pegasys.artemis.statetransition.util.exceptions;

import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

public class InvalidBlockAncestryException extends InvalidBlockException {
  public InvalidBlockAncestryException(
      final BeaconBlock block, final Checkpoint latestFinalizedCheckpoint) {
    super(
        "Invalid block at "
            + block.getSlot()
            + " does not descend from latest finalized block (root: "
            + latestFinalizedCheckpoint.getRoot()
            + "): "
            + block);
  }
}
