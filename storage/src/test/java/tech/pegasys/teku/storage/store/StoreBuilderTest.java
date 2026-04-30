/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;

class StoreBuilderTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @Test
  void forkChoiceStoreBuilder_shouldUseAnchorStateSlotAndRootWhenAnchorBlockPrecedesState()
      throws Exception {
    chainBuilder.generateGenesis();
    final UInt64 anchorStateSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedBlockAndState anchorBlock =
        chainBuilder.generateBlockAtSlot(anchorStateSlot.minus(1));
    final BeaconState anchorState = spec.processSlots(anchorBlock.getState(), anchorStateSlot);
    final Checkpoint checkpoint =
        new Checkpoint(spec.computeEpochAtSlot(anchorStateSlot), anchorBlock.getRoot());
    final AnchorPoint anchor =
        AnchorPoint.create(spec, checkpoint, anchorState, Optional.of(anchorBlock.getBlock()));

    final OnDiskStoreData storeData =
        StoreBuilder.forkChoiceStoreBuilder(spec, anchor, UInt64.ZERO);
    final StoredBlockMetadata metadata = storeData.blockInformation().get(anchor.getRoot());

    assertThat(metadata.getBlockSlot()).isEqualTo(anchorState.getSlot());
    assertThat(metadata.getStateRoot()).isEqualTo(anchorState.hashTreeRoot());
  }
}
