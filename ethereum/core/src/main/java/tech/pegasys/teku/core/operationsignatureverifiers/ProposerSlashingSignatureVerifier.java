package tech.pegasys.teku.core.operationsignatureverifiers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateCache;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_PROPOSER;

public class ProposerSlashingSignatureVerifier {

  private static final Logger LOG = LogManager.getLogger();

  public boolean verifySignature(BeaconState state,
                                 ProposerSlashing proposerSlashing,
                                 BLSSignatureVerifier signatureVerifier) {

    final BeaconBlockHeader header1 = proposerSlashing.getHeader_1().getMessage();
    final BeaconBlockHeader header2 = proposerSlashing.getHeader_2().getMessage();
    BLSPublicKey publicKey =
            BeaconStateCache.getTransitionCaches(state)
                    .getValidatorsPubKeys()
                    .get(
                            header1.getProposer_index(),
                            idx -> state.getValidators().get(toIntExact(idx.longValue())).getPubkey());

    if (signatureVerifier.verify(
            publicKey,
            compute_signing_root(
                    header1,
                    get_domain(state, DOMAIN_BEACON_PROPOSER, compute_epoch_at_slot(header1.getSlot()))),
            proposerSlashing.getHeader_1().getSignature())) {
      LOG.trace("Header1 signature is invalid {}", header1);
      return false;
    }

    if (signatureVerifier.verify(
            publicKey,
            compute_signing_root(
                    header2,
                    get_domain(state, DOMAIN_BEACON_PROPOSER, compute_epoch_at_slot(header2.getSlot()))),
            proposerSlashing.getHeader_2().getSignature())) {
      LOG.trace("Header2 signature is invalid {}", header1);
      return false;
    }
    return true;
  }
}
