package tech.pegasys.artemis.networking.eth2.gossip.topics.validation;

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.exceptions.EpochProcessingException;
import tech.pegasys.artemis.core.exceptions.SlotProcessingException;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.storage.client.RecentChainData;

import java.util.HashSet;
import java.util.Set;

import static com.google.common.primitives.UnsignedLong.valueOf;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.statetransition.forkchoice.ForkChoiceUtil.getCurrentSlot;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.util.config.Constants.MAXIMUM_GOSSIP_CLOCK_DISPARITY;

public class BlockValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;
  private final Set<UnsignedLong> receivedValidSignatureBlockSlots = new HashSet<>();

  public BlockValidator(RecentChainData recentChainData,
                        StateTransition stateTransition) {
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
  }

  public BlockValidationResult validate(SignedBeaconBlock block) {

    if (!blockSlotIsGreaterThanLatestFinalizedSlot(block) || !blockIsFirstBlockWithValidSignatureForSlot(block)) {
      LOG.trace("BlockValidator: Block is either too old or is not the first block with valid signature for " +
              "its slot. It will be dropped");
      return BlockValidationResult.INVALID;
    }

    final BeaconState preState = recentChainData.getStore().getBlockState(block.getMessage().getParent_root());
    if (!blockPreStateDoesExist(preState) || !blockIsNotFromFutureSlot(block)) {
      LOG.trace("BlockValidator: Either block pre state does not exist or block is from the future. " +
              "It will be saved for future processing");
      return BlockValidationResult.SAVED_FOR_FUTURE;
    }

    try {
      BeaconState postState = stateTransition.process_slots(preState, block.getMessage().getSlot());
      if (blockIsProposedByTheExpectedProposer(block, postState) &&
              blockSignatureIsValidWithRespectToProposerIndex(block, preState, postState)) {
        LOG.trace("BlockValidator: Block has passed all gossip layer validation");
        return BlockValidationResult.VALID;
      }
    } catch (EpochProcessingException | SlotProcessingException e) {
      LOG.error("BlockValidator: Unable to process block state.", e);
      return BlockValidationResult.INVALID;
    }

    return BlockValidationResult.INVALID;
  }

  private boolean blockIsNotFromFutureSlot(SignedBeaconBlock block) {
    ReadOnlyStore store = recentChainData.getStore();
    // .times() and .dividedBy() is to account for the fact that store.time is in seconds
    // and MAXIMUM_GOSSIP_CLOCK_DISPARITY is in ms.
    UnsignedLong maxTime = store.getTime()
            .times(valueOf(1000))
            .plus(MAXIMUM_GOSSIP_CLOCK_DISPARITY)
            .dividedBy(valueOf(1000));
    UnsignedLong maxCurrSlot = getCurrentSlot(maxTime, store.getGenesisTime());
    return block.getSlot().compareTo(maxCurrSlot) <= 0;
  }

  private boolean blockSlotIsGreaterThanLatestFinalizedSlot(SignedBeaconBlock block) {
    UnsignedLong finalizedSlot = recentChainData.getStore().getFinalizedCheckpoint().getEpochStartSlot();
    return block.getSlot().compareTo(finalizedSlot) > 0;
  }

  private boolean blockPreStateDoesExist(BeaconState preState) {
    return preState != null;
  }

  private boolean blockIsFirstBlockWithValidSignatureForSlot(SignedBeaconBlock block) {
    return !receivedValidSignatureBlockSlots.contains(block.getSlot());
  }

  private boolean blockSignatureIsValidWithRespectToProposerIndex(SignedBeaconBlock block,
                                                                  BeaconState preState,
                                                                  BeaconState postState) {
      Validator proposer = postState.getValidators().get(
              toIntExact(block.getMessage().getProposer_index().longValue())
      );

      final Bytes domain = get_domain(preState, DOMAIN_BEACON_PROPOSER);
      final Bytes signing_root = compute_signing_root(block.getMessage(), domain);
      final BLSSignature signature = block.getSignature();
      boolean signatureValid = BLS.verify(proposer.getPubkey(), signing_root, signature);

      if (signatureValid) {
        receivedValidSignatureBlockSlots.add(block.getSlot());
      }

      return signatureValid;
  }

  private boolean blockIsProposedByTheExpectedProposer(SignedBeaconBlock block,
                                                       BeaconState postState) {
      final int proposerIndex = get_beacon_proposer_index(postState);
      return proposerIndex == toIntExact(block.getMessage().getProposer_index().longValue());
  }
}
