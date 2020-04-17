package tech.pegasys.artemis.core.blockvalidator;

import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.async.SafeFuture;

public class NopBlockValidator implements BlockValidator {

  @Override
  public SafeFuture<BlockValidationResult> validatePreState(BeaconState preState,
      SignedBeaconBlock block) {
    return SafeFuture.completedFuture(new BlockValidationResult(true));
  }

  @Override
  public SafeFuture<BlockValidationResult> validatePostState(BeaconState postState, SignedBeaconBlock block) {
    return SafeFuture.completedFuture(new BlockValidationResult(true));
  }
}
