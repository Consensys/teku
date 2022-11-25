/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.eip4844.block;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapellaTest;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockProcessorEip4844Test extends BlockProcessorCapellaTest {
  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetEip4844();
  }

  @Test
  void shouldRejectCapellaBlock() {
    final DataStructureUtil data = new DataStructureUtil(TestSpecFactory.createMinimalCapella());
    BeaconState preState = createBeaconState();
    final SignedBeaconBlock block = data.randomSignedBeaconBlock(preState.getSlot().increment());
    assertThatThrownBy(
            () ->
                spec.processBlock(
                    preState,
                    block,
                    BLSSignatureVerifier.SIMPLE,
                    Optional.empty(),
                    KzgCommitmentsProcessor.NOOP,
                    BlobsSidecarAvailabilityChecker.NOOP))
        .isInstanceOf(StateTransitionException.class);
  }
}
