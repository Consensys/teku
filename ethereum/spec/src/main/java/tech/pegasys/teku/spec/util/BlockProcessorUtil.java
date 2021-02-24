/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSSignatureVerifier.InvalidSignatureException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.util.operationsignatureverifiers.ProposerSlashingSignatureVerifier;
import tech.pegasys.teku.spec.util.operationsignatureverifiers.VoluntaryExitSignatureVerifier;
import tech.pegasys.teku.spec.util.operationvalidators.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.util.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.spec.util.operationvalidators.OperationInvalidReason;
import tech.pegasys.teku.spec.util.operationvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.spec.util.operationvalidators.VoluntaryExitStateTransitionValidator;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public final class BlockProcessorUtil {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConstants specConstants;
  private final BeaconStateUtil beaconStateUtil;
  private final AttestationUtil attestationUtil;
  private final ValidatorsUtil validatorsUtil;

  public BlockProcessorUtil(
      final SpecConstants specConstants,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil) {
    this.specConstants = specConstants;
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.validatorsUtil = validatorsUtil;
  }

  /**
   * Processes block header
   *
   * @param state
   * @param blockHeader
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#block-header</a>
   */
  public void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    try {
      checkArgument(
          blockHeader.getSlot().equals(state.getSlot()),
          "process_block_header: Verify that the slots match");
      checkArgument(
          blockHeader.getProposerIndex().longValue()
              == beaconStateUtil.getBeaconProposerIndex(state),
          "process_block_header: Verify that proposer index is the correct index");
      checkArgument(
          blockHeader.getParentRoot().equals(state.getLatest_block_header().hashTreeRoot()),
          "process_block_header: Verify that the parent matches");
      checkArgument(
          blockHeader.getSlot().compareTo(state.getLatest_block_header().getSlot()) > 0,
          "process_block_header: Verify that the block is newer than latest block header");

      // Cache the current block as the new latest block
      state.setLatest_block_header(
          new BeaconBlockHeader(
              blockHeader.getSlot(),
              blockHeader.getProposerIndex(),
              blockHeader.getParentRoot(),
              Bytes32.ZERO, // Overwritten in the next `process_slot` call
              blockHeader.getBodyRoot()));

      // Only if we are processing blocks (not proposing them)
      Validator proposer =
          state.getValidators().get(toIntExact(blockHeader.getProposerIndex().longValue()));
      checkArgument(!proposer.isSlashed(), "process_block_header: Verify proposer is not slashed");

    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public void processRandaoNoValidation(MutableBeaconState state, BeaconBlockBody body)
      throws BlockProcessingException {
    try {
      UInt64 epoch = beaconStateUtil.getCurrentEpoch(state);

      Bytes32 mix =
          beaconStateUtil
              .getRandaoMix(state, epoch)
              .xor(Hash.sha2_256(body.getRandao_reveal().toSSZBytes()));
      int index = epoch.mod(specConstants.getEpochsPerHistoricalVector()).intValue();
      state.getRandao_mixes().set(index, mix);
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public void verifyRandao(BeaconState state, BeaconBlock block, BLSSignatureVerifier bls)
      throws InvalidSignatureException {
    UInt64 epoch = beaconStateUtil.computeEpochAtSlot(block.getSlot());
    // Verify RANDAO reveal
    final BLSPublicKey proposerPublicKey =
        validatorsUtil.getValidatorPubKey(state, block.getProposerIndex()).orElseThrow();
    final Bytes32 domain = beaconStateUtil.getDomain(state, specConstants.getDomainRandao());
    final Bytes signing_root = beaconStateUtil.computeSigningRoot(epoch, domain);
    bls.verifyAndThrow(
        proposerPublicKey,
        signing_root,
        block.getBody().getRandao_reveal(),
        "process_randao: Verify that the provided randao value is valid");
  }

  /**
   * Processes Eth1 data
   *
   * @param state
   * @param body
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#eth1-data</a>
   */
  public void processEth1Data(MutableBeaconState state, BeaconBlockBody body) {
    state.getEth1_data_votes().add(body.getEth1_data());
    long vote_count = getVoteCount(state, body.getEth1_data());
    if (isEnoughVotesToUpdateEth1Data(vote_count)) {
      state.setEth1_data(body.getEth1_data());
    }
  }

  public boolean isEnoughVotesToUpdateEth1Data(long voteCount) {
    return voteCount * 2
        > (long) specConstants.getEpochsPerEth1VotingPeriod() * specConstants.getSlotsPerEpoch();
  }

  public long getVoteCount(BeaconState state, Eth1Data eth1Data) {
    return state.getEth1_data_votes().stream().filter(item -> item.equals(eth1Data)).count();
  }

  /**
   * Processes all block body operations
   *
   * @param state
   * @param body
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#operations</a>
   */
  public void processOperationsNoValidation(MutableBeaconState state, BeaconBlockBody body)
      throws BlockProcessingException {
    try {

      checkArgument(
          body.getDeposits().size()
              == Math.min(
                  specConstants.getMaxDeposits(),
                  toIntExact(
                      state
                          .getEth1_data()
                          .getDeposit_count()
                          .minus(state.getEth1_deposit_index())
                          .longValue())),
          "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");

      processProposerSlashingsNoValidation(state, body.getProposer_slashings());
      processAttesterSlashings(state, body.getAttester_slashings());
      processAttestationsNoValidation(state, body.getAttestations());
      processDeposits(state, body.getDeposits());
      processVoluntaryExitsNoValidation(state, body.getVoluntary_exits());
      // @process_shard_receipt_proofs
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes proposer slashings
   *
   * @param state
   * @param proposerSlashings
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#proposer-slashings</a>
   */
  public void processProposerSlashings(
      MutableBeaconState state, SSZList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
    processProposerSlashingsNoValidation(state, proposerSlashings);
    boolean signaturesValid =
        verifyProposerSlashings(state, proposerSlashings, BLSSignatureVerifier.SIMPLE);
    if (!signaturesValid) {
      throw new BlockProcessingException("Slashing signature is invalid");
    }
  }

  public void processProposerSlashingsNoValidation(
      MutableBeaconState state, SSZList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
    ProposerSlashingStateTransitionValidator validator =
        new ProposerSlashingStateTransitionValidator();

    try {
      // For each proposer_slashing in block.body.proposer_slashings:
      for (ProposerSlashing proposerSlashing : proposerSlashings) {
        Optional<OperationInvalidReason> invalidReason =
            validator.validate(state, proposerSlashing);
        checkArgument(
            invalidReason.isEmpty(),
            "process_proposer_slashings: %s",
            invalidReason.map(OperationInvalidReason::describe).orElse(""));

        beaconStateUtil.slashValidator(
            state,
            toIntExact(proposerSlashing.getHeader_1().getMessage().getProposerIndex().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public boolean verifyProposerSlashings(
      BeaconState state,
      SSZList<ProposerSlashing> proposerSlashings,
      BLSSignatureVerifier signatureVerifier) {
    ProposerSlashingSignatureVerifier slashingSignatureVerifier =
        new ProposerSlashingSignatureVerifier();

    // For each proposer_slashing in block.body.proposer_slashings:
    for (ProposerSlashing proposerSlashing : proposerSlashings) {

      boolean slashingSignatureValid =
          slashingSignatureVerifier.verifySignature(state, proposerSlashing, signatureVerifier);
      if (!slashingSignatureValid) {
        LOG.trace("Proposer slashing signature is invalid {}", proposerSlashing);
        return false;
      }
    }
    return true;
  }

  /**
   * Processes attester slashings
   *
   * @param state
   * @param attesterSlashings
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#attester-slashings</a>
   */
  public void processAttesterSlashings(
      MutableBeaconState state, SSZList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException {
    try {
      final AttesterSlashingStateTransitionValidator validator =
          new AttesterSlashingStateTransitionValidator();

      // For each attester_slashing in block.body.attester_slashings:
      for (AttesterSlashing attesterSlashing : attesterSlashings) {
        List<UInt64> indicesToSlash = new ArrayList<>();
        final Optional<OperationInvalidReason> invalidReason =
            validator.validate(state, attesterSlashing, indicesToSlash);

        checkArgument(
            invalidReason.isEmpty(),
            "process_attester_slashings: %s",
            invalidReason.map(OperationInvalidReason::describe).orElse(""));

        indicesToSlash.forEach(
            indexToSlash ->
                beaconStateUtil.slashValidator(state, toIntExact(indexToSlash.longValue())));
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes attestations
   *
   * @param state
   * @param attestations
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#attestations</a>
   */
  public void processAttestations(MutableBeaconState state, SSZList<Attestation> attestations)
      throws BlockProcessingException {
    processAttestations(state, attestations, IndexedAttestationCache.NOOP);
  }

  /**
   * Processes attestations
   *
   * @param state
   * @param attestations
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#attestations</a>
   */
  public void processAttestations(
      MutableBeaconState state,
      SSZList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    processAttestationsNoValidation(state, attestations);
    verifyAttestations(state, attestations, BLSSignatureVerifier.SIMPLE, indexedAttestationCache);
  }

  public void processAttestationsNoValidation(
      MutableBeaconState state, SSZList<Attestation> attestations) throws BlockProcessingException {
    try {
      final AttestationDataStateTransitionValidator validator =
          new AttestationDataStateTransitionValidator();

      for (Attestation attestation : attestations) {
        AttestationData data = attestation.getData();
        final Optional<OperationInvalidReason> invalidReason = validator.validate(state, data);
        checkArgument(
            invalidReason.isEmpty(),
            "process_attestations: %s",
            invalidReason.map(OperationInvalidReason::describe).orElse(""));

        List<Integer> committee =
            beaconStateUtil.getBeaconCommittee(state, data.getSlot(), data.getIndex());
        checkArgument(
            attestation.getAggregation_bits().size() == committee.size(),
            "process_attestations: Attestation aggregation bits and committee don't have the same length");

        PendingAttestation pendingAttestation =
            new PendingAttestation(
                attestation.getAggregation_bits(),
                data,
                state.getSlot().minus(data.getSlot()),
                UInt64.valueOf(beaconStateUtil.getBeaconProposerIndex(state)));

        if (data.getTarget().getEpoch().equals(beaconStateUtil.getCurrentEpoch(state))) {
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        } else {
          state.getPrevious_epoch_attestations().add(pendingAttestation);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public void verifyAttestations(
      BeaconState state,
      SSZList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {

    Optional<AttestationProcessingResult> processResult =
        attestations.stream()
            .map(
                attestation ->
                    indexedAttestationCache.computeIfAbsent(
                        attestation,
                        () -> attestationUtil.getIndexedAttestation(state, attestation)))
            .map(
                attestation ->
                    attestationUtil.isValidIndexedAttestation(
                        state, attestation, signatureVerifier))
            .filter(result -> !result.isSuccessful())
            .findAny();
    if (processResult.isPresent()) {
      throw new BlockProcessingException(
          "Invalid attestation: " + processResult.get().getInvalidReason());
    }
  }

  /**
   * Processes deposits
   *
   * @param state
   * @param deposits
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#deposits</a>
   */
  public void processDeposits(MutableBeaconState state, SSZList<? extends Deposit> deposits)
      throws BlockProcessingException {
    try {
      for (Deposit deposit : deposits) {
        beaconStateUtil.processDeposit(state, deposit);
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Processes voluntary exits
   *
   * @param state
   * @param exits
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#voluntary-exits</a>
   */
  public void processVoluntaryExits(MutableBeaconState state, SSZList<SignedVoluntaryExit> exits)
      throws BlockProcessingException {

    processVoluntaryExitsNoValidation(state, exits);
    boolean signaturesValid = verifyVoluntaryExits(state, exits, BLSSignatureVerifier.SIMPLE);
    if (!signaturesValid) {
      throw new BlockProcessingException("Exit signature is invalid");
    }
  }

  public void processVoluntaryExitsNoValidation(
      MutableBeaconState state, SSZList<SignedVoluntaryExit> exits)
      throws BlockProcessingException {
    VoluntaryExitStateTransitionValidator validator = new VoluntaryExitStateTransitionValidator();
    try {

      // For each exit in block.body.voluntaryExits:
      for (SignedVoluntaryExit signedExit : exits) {
        Optional<OperationInvalidReason> invalidReason = validator.validate(state, signedExit);
        checkArgument(
            invalidReason.isEmpty(),
            "process_voluntary_exits: %s",
            invalidReason.map(OperationInvalidReason::describe).orElse(""));

        // - Run initiate_validator_exit(state, exit.validator_index)
        beaconStateUtil.initiateValidatorExit(
            state, toIntExact(signedExit.getMessage().getValidator_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public boolean verifyVoluntaryExits(
      BeaconState state,
      SSZList<SignedVoluntaryExit> exits,
      BLSSignatureVerifier signatureVerifier) {
    VoluntaryExitSignatureVerifier verifier = new VoluntaryExitSignatureVerifier();
    for (SignedVoluntaryExit signedExit : exits) {
      boolean exitSignatureValid = verifier.verifySignature(state, signedExit, signatureVerifier);
      if (!exitSignatureValid) {
        LOG.trace("Exit signature is invalid {}", signedExit);
        return false;
      }
    }
    return true;
  }
}
