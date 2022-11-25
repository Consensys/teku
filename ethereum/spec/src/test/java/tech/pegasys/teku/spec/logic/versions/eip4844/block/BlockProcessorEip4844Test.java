package tech.pegasys.teku.spec.logic.versions.eip4844.block;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapellaTest;
import tech.pegasys.teku.spec.logic.versions.eip4844.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.eip4844.block.KzgCommitmentsProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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
