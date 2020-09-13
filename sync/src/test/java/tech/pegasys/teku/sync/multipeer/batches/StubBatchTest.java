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

package tech.pegasys.teku.sync.multipeer.batches;

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StubBatchTest extends AbstractBatchTest {

  @Override
  protected Batch createBatch(final long startSlot, final long count) {
    return new StubBatch(targetChain, UInt64.valueOf(startSlot), UInt64.valueOf(count));
  }

  @Override
  protected void receiveBlocks(final Batch batch, final SignedBeaconBlock... blocks) {
    ((StubBatch) batch).receiveBlocks(blocks);
  }
}
