package tech.pegasys.teku.core.operationsignatureverifiers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateCache;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_VOLUNTARY_EXIT;

public class VoluntaryExitSignatureVerifier {

  public boolean verifySignature(BeaconState state,
                                 SignedVoluntaryExit signedExit,
                                 BLSSignatureVerifier signatureVerifier) {
    final VoluntaryExit exit = signedExit.getMessage();

    BLSPublicKey publicKey =
            BeaconStateCache.getTransitionCaches(state)
                    .getValidatorsPubKeys()
                    .get(
                            exit.getValidator_index(),
                            idx -> state.getValidators().get(toIntExact(idx.longValue())).getPubkey());

    final Bytes domain = get_domain(state, DOMAIN_VOLUNTARY_EXIT, exit.getEpoch());
    final Bytes signing_root = compute_signing_root(exit, domain);
    return signatureVerifier.verify(publicKey, signing_root, signedExit.getSignature());
  }
}
