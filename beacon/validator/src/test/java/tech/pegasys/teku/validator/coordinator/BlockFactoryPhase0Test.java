/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.validator.coordinator;

import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateAssert.assertThatSyncAggregate;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockFactoryPhase0Test extends AbstractBlockFactoryTest {

  @Test
  public void shouldCreateBlockAfterNormalSlot() {
    assertBlockCreated(1, TestSpecFactory.createMinimalPhase0(), false, state -> {}, false);
  }

  @Test
  public void shouldCreateBlockAfterSkippedSlot() {
    assertBlockCreated(2, TestSpecFactory.createMinimalPhase0(), false, state -> {}, false);
  }

  @Test
  public void shouldCreateBlockAfterMultipleSkippedSlot() {
    assertBlockCreated(5, TestSpecFactory.createMinimalPhase0(), false, state -> {}, false);
  }

  @Test
  void shouldIncludeSyncAggregateWhenAltairIsActive() {
    final BeaconBlock block =
        assertBlockCreated(1, TestSpecFactory.createMinimalAltair(), false, state -> {}, false)
            .getBlock();
    final SyncAggregate result = getSyncAggregate(block);
    assertThatSyncAggregate(result).isNotNull();
    verify(syncCommitteeContributionPool)
        .createSyncAggregateForBlock(UInt64.ONE, block.getParentRoot());
  }

  @Override
  public BlockFactory createBlockFactory(final Spec spec) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final Bytes32 graffiti = dataStructureUtil.randomBytes32();
    return new BlockFactoryPhase0(
        spec,
        new BlockOperationSelectorFactory(
            spec,
            attestationsPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            blsToExecutionChangePool,
            syncCommitteeContributionPool,
            depositProvider,
            eth1DataCache,
            graffiti,
            executionLayer));
  }
}
