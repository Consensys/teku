package tech.pegasys.artemis.core.blockvalidator;

import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.async.SafeFuture;

public interface BlockValidator {

  class BlockValidationResult {
    private final boolean isValid;
    private final Exception reason;

    public BlockValidationResult(Exception reason) {
      this.isValid = false;
      this.reason = reason;
    }

    public BlockValidationResult(boolean isValid) {
      this.isValid = isValid;
      reason = null;
    }

    public boolean isValid() {
      return isValid;
    }

    public Exception getReason() {
      return reason;
    }
  }

  SafeFuture<BlockValidationResult> validatePreState(BeaconState preState, SignedBeaconBlock block);

  SafeFuture<BlockValidationResult> validatePostState(BeaconState postState,
      SignedBeaconBlock block);

  default SafeFuture<BlockValidationResult> validate(
      BeaconState preState, SignedBeaconBlock block, BeaconState postState) {
    SafeFuture<BlockValidationResult> preFut = validatePreState(preState, block);
    SafeFuture<BlockValidationResult> postFut = validatePostState(postState, block);
    return preFut.thenCombine(postFut, (pre, post) -> !pre.isValid() ? pre : post);
  }
}
