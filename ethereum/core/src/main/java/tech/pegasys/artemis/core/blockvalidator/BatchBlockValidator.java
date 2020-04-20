package tech.pegasys.artemis.core.blockvalidator;

import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.async.SafeFuture;

public class BatchBlockValidator implements BlockValidator {

  @Override
  public SafeFuture<BlockValidationResult> validatePreState(BeaconState preState,
      SignedBeaconBlock block) {
    BatchSignatureVerifier signatureVerifier = new BatchSignatureVerifier();
    SimpleBlockValidator blockValidator = new SimpleBlockValidator(true, true, true,
        signatureVerifier);
    SafeFuture<BlockValidationResult> noBLSValidationResultFut = blockValidator
        .validatePreState(preState, block);
    if (!noBLSValidationResultFut.join().isValid()) {
      return noBLSValidationResultFut;
    } else {
      boolean batchBLSResult = signatureVerifier.batchVerify();
      if (!batchBLSResult) {
        // validate again naively to get exact invalid signature
        return new SimpleBlockValidator().validatePreState(preState, block);
      } else {
        return SafeFuture.completedFuture(new BlockValidationResult(true));
      }
    }
  }

  @Override
  public SafeFuture<BlockValidationResult> validatePostState(BeaconState postState,
      SignedBeaconBlock block) {
    return new SimpleBlockValidator().validatePostState(postState, block);
  }
}
