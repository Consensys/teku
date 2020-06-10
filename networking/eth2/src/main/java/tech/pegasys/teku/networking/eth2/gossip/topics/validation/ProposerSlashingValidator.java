package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.core.operationsignatureverifiers.ProposerSlashingSignatureVerifier;
import tech.pegasys.teku.core.operationstatetransitionvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationstatetransitionvalidators.ProposerSlashingStateTransitionValidator.ProposerSlashingInvalidReason;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;

import java.util.Optional;
import java.util.Set;

import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.util.config.Constants.VALID_VALIDATOR_SET_SIZE;

public class ProposerSlashingValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Set<UnsignedLong> receivedValidExitSet =
          ConcurrentLimitedSet.create(
                  VALID_VALIDATOR_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
  private final ProposerSlashingStateTransitionValidator transitionValidator;
  private final ProposerSlashingSignatureVerifier signatureValidator;

  public ProposerSlashingValidator(RecentChainData recentChainData,
                                   ProposerSlashingStateTransitionValidator proposerSlashingStateTransitionValidator,
                                   ProposerSlashingSignatureVerifier proposerSlashingSignatureVerifier) {
    this.recentChainData = recentChainData;
    this.transitionValidator = proposerSlashingStateTransitionValidator;
    this.signatureValidator = proposerSlashingSignatureVerifier;
  }

  public InternalValidationResult validate(ProposerSlashing slashing) {
    if (!isFirstValidSlashingForValidator(slashing)) {
      LOG.trace("ProposerSlashingValidator: Exit is not the first one for the given validator.");
      return IGNORE;
    }

    if (!passesProcessProposerSlashingConditions(slashing)) {
      return REJECT;
    }

    if (receivedValidExitSet.add(slashing.getHeader_1().getMessage().getProposer_index())) {
      return ACCEPT;
    } else {
      LOG.trace("ProposerSlashingValidator: Exit is not the first one for the given validator.");
      return IGNORE;
    }
  }

  private boolean passesProcessProposerSlashingConditions(ProposerSlashing slashing) {
    BeaconState state =
            recentChainData
                    .getBestState()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "Unable to get best state for proposer slashing processing."));
    Optional<ProposerSlashingInvalidReason> invalidReason =
            transitionValidator.validateSlashing(state, slashing);

    if (invalidReason.isPresent()) {
      LOG.trace(
              "ProposerSlashingValidator: Slashing fails process proposer slashing conditions {}.",
              invalidReason.map(ProposerSlashingInvalidReason::describe).orElse(""));
      return false;
    }


    if (!signatureValidator.verifySignature(state, slashing, BLSSignatureVerifier.SIMPLE)) {
      LOG.trace("ProposerSlashingValidator: Slashing fails signature verification.");
      return false;
    }
    return true;
  }

  private boolean isFirstValidSlashingForValidator(ProposerSlashing slashing) {
    return !receivedValidExitSet.contains(slashing.getHeader_1().getMessage().getProposer_index());
  }
}
