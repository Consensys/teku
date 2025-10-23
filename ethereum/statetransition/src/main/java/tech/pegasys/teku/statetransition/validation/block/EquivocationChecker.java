/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.validation.block;

import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;

import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class EquivocationChecker {

  private final Map<SlotAndProposerIndex, Bytes32> receivedValidBlockRoots =
      LimitedMap.createNonSynchronized(VALID_BLOCK_SET_SIZE);

  public synchronized EquivocationCheckResult check(final SignedBeaconBlock block) {
    final SlotAndProposerIndex key = new SlotAndProposerIndex(block);
    final Bytes32 blockRoot = block.getRoot();

    final Bytes32 previouslySeenRoot = receivedValidBlockRoots.get(key);

    if (previouslySeenRoot == null) {
      return EquivocationCheckResult.FIRST_BLOCK_FOR_SLOT_PROPOSER;
    }
    if (previouslySeenRoot.equals(blockRoot)) {
      return EquivocationCheckResult.BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER;
    }
    return EquivocationCheckResult.EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER;
  }

  public synchronized void markBlockAsSeen(final SignedBeaconBlock block) {
    final SlotAndProposerIndex key = new SlotAndProposerIndex(block);
    receivedValidBlockRoots.putIfAbsent(key, block.getRoot());
  }

  public enum EquivocationCheckResult {
    FIRST_BLOCK_FOR_SLOT_PROPOSER,
    BLOCK_ALREADY_SEEN_FOR_SLOT_PROPOSER,
    EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER
  }
}
