package tech.pegasys.teku.core.blockvalidator;

import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.util.async.SafeFuture;

public class FuzzingBlockValidator implements BlockValidator {
  @Override
  public SafeFuture<BlockValidationResult> validatePreState(BeaconState preState, SignedBeaconBlock block) {
    return SafeFuture.completedFuture(new BlockValidationResult(true));
  }

  @Override
  public SafeFuture<BlockValidationResult> validatePostState(BeaconState postState, SignedBeaconBlock block) {
    if (!block.getMessage().getState_root().equals(postState.hashTreeRoot())) {
      return SafeFuture.completedFuture(
              new BlockValidationResult(
                      new StateTransitionException(
                              "Block state root does NOT match the calculated state root!\n"
                                      + "Block state root: "
                                      + block.getMessage().getState_root().toHexString()
                                      + "New state root: "
                                      + postState.hashTreeRoot().toHexString())));
    } else {
      return SafeFuture.completedFuture(new BlockValidationResult(true));
    }
  }
}
