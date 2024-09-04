/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class CanonicalBlockResolverStub implements CanonicalBlockResolver {

  private final Map<UInt64, BeaconBlock> chain = new HashMap<>();

  private final DataStructureUtil dataStructureUtil;
  private AtomicLong blockAccessCounter = new AtomicLong();

  public CanonicalBlockResolverStub(Spec spec) {
    dataStructureUtil = new DataStructureUtil(0, spec);
  }

  public BeaconBlock addBlock(int slot, boolean hasBlobs) {
    return addBlock(slot, hasBlobs ? 1 : 0);
  }

  public BeaconBlock addBlock(int slot, int blobCount) {
    UInt64 slotU = UInt64.valueOf(slot);
    BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(blobCount);
    BeaconBlock block = dataStructureUtil.randomBeaconBlock(slotU, beaconBlockBody);
    addBlock(block);
    return block;
  }

  public void addBlock(BeaconBlock block) {
    chain.put(block.getSlot(), block);
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> getBlockAtSlot(UInt64 slot) {
    blockAccessCounter.incrementAndGet();
    return SafeFuture.completedFuture(Optional.ofNullable(chain.get(slot)));
  }

  public AtomicLong getBlockAccessCounter() {
    return blockAccessCounter;
  }
}
