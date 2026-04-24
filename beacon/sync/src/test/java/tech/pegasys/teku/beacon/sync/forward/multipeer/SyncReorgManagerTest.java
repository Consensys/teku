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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncReorgManagerTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ForkChoiceTrigger forkChoiceTrigger = mock(ForkChoiceTrigger.class);

  private final SyncReorgManager syncReorgManager =
      new SyncReorgManager(recentChainData, forkChoiceTrigger);

  @Test
  public void onBlocksImported_shouldDoNothingIfNoCurrentHead() {
    when(recentChainData.getChainHead()).thenReturn(Optional.empty());
    syncReorgManager.onBlocksImported(dataStructureUtil.randomSignedBeaconBlock());

    verifyNoInteractions(forkChoiceTrigger);
  }

  @Test
  public void onBlocksImported_shouldDoNothingIfLastImportedBlockIsCurrentHead() {
    final SignedBlockAndState headBlock = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    when(recentChainData.getChainHead()).thenReturn(Optional.of(ChainHead.create(headBlock)));
    syncReorgManager.onBlocksImported(headBlock.getBlock());

    verifyNoInteractions(forkChoiceTrigger);
  }

  @Test
  public void onBlocksImported_shouldDoNothingIfLastImportedBlockIsWithinReorgThreshold() {
    final SignedBlockAndState headBlock = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final SignedBeaconBlock lastImportedBlock =
        dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(10));
    when(recentChainData.getChainHead()).thenReturn(Optional.of(ChainHead.create(headBlock)));
    syncReorgManager.onBlocksImported(lastImportedBlock);

    verifyNoInteractions(forkChoiceTrigger);
  }

  @Test
  public void onBlocksImported_shouldTriggerReorgWhenLastImportedBlockIsOutsideReorgThreshold() {
    final SignedBlockAndState headBlock = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final SignedBeaconBlock lastImportedBlock =
        dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(11));
    when(recentChainData.getChainHead()).thenReturn(Optional.of(ChainHead.create(headBlock)));
    syncReorgManager.onBlocksImported(lastImportedBlock);

    verify(forkChoiceTrigger).reorgWhileSyncing(headBlock.getRoot(), lastImportedBlock.getRoot());
  }
}
