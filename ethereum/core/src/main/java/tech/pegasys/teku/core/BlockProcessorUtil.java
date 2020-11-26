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

package tech.pegasys.teku.core;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.AttestationUtil.is_valid_indexed_attestation;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.process_deposit;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.slash_validator;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_RANDAO;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.teku.util.config.Constants.MAX_DEPOSITS;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

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
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.core.lookup.IndexedAttestationProvider;
import tech.pegasys.teku.core.operationsignatureverifiers.ProposerSlashingSignatureVerifier;
import tech.pegasys.teku.core.operationsignatureverifiers.VoluntaryExitSignatureVerifier;
import tech.pegasys.teku.core.operationvalidators.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.OperationInvalidReason;
import tech.pegasys.teku.core.operationvalidators.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.core.operationvalidators.VoluntaryExitStateTransitionValidator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
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
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public final class BlockProcessorUtil {

  private static final Logger LOG = LogManager.getLogger();

  /**
   * Processes block header
   *
   * @param state
   * @param block
   * @throws BlockProcessingException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#block-header</a>
   */
  public static void process_block_header(MutableBeaconState state, BeaconBlock block)
      throws BlockProcessingException {
    try {
      checkArgument(
          block.getSlot().equals(state.getSlot()),
          "process_block_header: Verify that the slots match");
      checkArgument(
          block.getProposerIndex().longValue() == get_beacon_proposer_index(state),
          "process_block_header: Verify that proposer index is the correct index");
      checkArgument(
          block.getParentRoot().equals(state.getLatest_block_header().hash_tree_root()),
          "process_block_header: Verify that the parent matches");
      checkArgument(
          block.getSlot().compareTo(state.getLatest_block_header().getSlot()) > 0,
          "process_block_header: Verify that the block is newer than latest block header");

      // Cache the current block as the new latest block
      state.setLatest_block_header(
          new BeaconBlockHeader(
              block.getSlot(),
              block.getProposerIndex(),
              block.getParentRoot(),
              Bytes32.ZERO, // Overwritten in the next `process_slot` call
              block.getBody().hash_tree_root()));

      // Only if we are processing blocks (not proposing them)
      Validator proposer =
          state.getValidators().get(toIntExact(block.getProposerIndex().longValue()));
      checkArgument(!proposer.isSlashed(), "process_block_header: Verify proposer is not slashed");

    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public static void process_randao_no_validation(MutableBeaconState state, BeaconBlockBody body)
      throws BlockProcessingException {
    try {
      UInt64 epoch = get_current_epoch(state);

      Bytes32 mix =
          get_randao_mix(state, epoch).xor(Hash.sha2_256(body.getRandao_reveal().toSSZBytes()));
      int index = epoch.mod(EPOCHS_PER_HISTORICAL_VECTOR).intValue();
      state.getRandao_mixes().set(index, mix);
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public static void verify_randao(BeaconState state, BeaconBlock block, BLSSignatureVerifier bls)
      throws InvalidSignatureException {
    UInt64 epoch = compute_epoch_at_slot(block.getSlot());
    // Verify RANDAO reveal
    final BLSPublicKey proposerPublicKey =
        ValidatorsUtil.getValidatorPubKey(state, block.getProposerIndex()).orElseThrow();
    final Bytes signing_root =
        compute_signing_root(epoch.longValue(), get_domain(state, DOMAIN_RANDAO));
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
  public static void process_eth1_data(MutableBeaconState state, BeaconBlockBody body) {
    state.getEth1_data_votes().add(body.getEth1_data());
    long vote_count = getVoteCount(state, body.getEth1_data());
    if (isEnoughVotesToUpdateEth1Data(vote_count)) {
      state.setEth1_data(body.getEth1_data());
    }
  }

  public static boolean isEnoughVotesToUpdateEth1Data(long voteCount) {
    return voteCount * 2 > EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH;
  }

  public static long getVoteCount(BeaconState state, Eth1Data eth1Data) {
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
  public static void process_operations_no_validation(
      MutableBeaconState state, BeaconBlockBody body) throws BlockProcessingException {
    try {

      checkArgument(
          body.getDeposits().size()
              == Math.min(
                  MAX_DEPOSITS,
                  toIntExact(
                      state
                          .getEth1_data()
                          .getDeposit_count()
                          .minus(state.getEth1_deposit_index())
                          .longValue())),
          "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");

      process_proposer_slashings_no_validation(state, body.getProposer_slashings());
      process_attester_slashings(state, body.getAttester_slashings());
      process_attestations_no_validation(state, body.getAttestations());
      process_deposits(state, body.getDeposits());
      process_voluntary_exits_no_validation(state, body.getVoluntary_exits());
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
  public static void process_proposer_slashings(
      MutableBeaconState state, SSZList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
    process_proposer_slashings_no_validation(state, proposerSlashings);
    boolean signaturesValid =
        verify_proposer_slashings(state, proposerSlashings, BLSSignatureVerifier.SIMPLE);
    if (!signaturesValid) {
      throw new BlockProcessingException("Slashing signature is invalid");
    }
  }

  public static void process_proposer_slashings_no_validation(
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

        slash_validator(
            state,
            toIntExact(proposerSlashing.getHeader_1().getMessage().getProposerIndex().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public static boolean verify_proposer_slashings(
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
  public static void process_attester_slashings(
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
            indexToSlash -> slash_validator(state, toIntExact(indexToSlash.longValue())));
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
  public static void process_attestations(
      MutableBeaconState state,
      SSZList<Attestation> attestations,
      IndexedAttestationProvider indexedAttestationProvider)
      throws BlockProcessingException {
    process_attestations_no_validation(state, attestations);
    verify_attestations(
        state, attestations, BLSSignatureVerifier.SIMPLE, indexedAttestationProvider);
  }

  public static void process_attestations_no_validation(
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

        List<Integer> committee = get_beacon_committee(state, data.getSlot(), data.getIndex());
        checkArgument(
            attestation.getAggregation_bits().getCurrentSize() == committee.size(),
            "process_attestations: Attestation aggregation bits and committee don't have the same length");

        PendingAttestation pendingAttestation =
            new PendingAttestation(
                attestation.getAggregation_bits(),
                data,
                state.getSlot().minus(data.getSlot()),
                UInt64.valueOf(get_beacon_proposer_index(state)));

        if (data.getTarget().getEpoch().equals(get_current_epoch(state))) {
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

  public static void verify_attestations(
      BeaconState state,
      SSZList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationProvider indexedAttestationProvider)
      throws BlockProcessingException {

    Optional<AttestationProcessingResult> processResult =
        attestations.stream()
            .map(attesation -> indexedAttestationProvider.getIndexedAttestation(state, attesation))
            .map(attestation -> is_valid_indexed_attestation(state, attestation, signatureVerifier))
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
  public static void process_deposits(MutableBeaconState state, SSZList<? extends Deposit> deposits)
      throws BlockProcessingException {
    try {
      for (Deposit deposit : deposits) {
        process_deposit(state, deposit);
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
  public static void process_voluntary_exits(
      MutableBeaconState state, SSZList<SignedVoluntaryExit> exits)
      throws BlockProcessingException {

    process_voluntary_exits_no_validation(state, exits);
    boolean signaturesValid = verify_voluntary_exits(state, exits, BLSSignatureVerifier.SIMPLE);
    if (!signaturesValid) {
      throw new BlockProcessingException("Exit signature is invalid");
    }
  }

  public static void process_voluntary_exits_no_validation(
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
        initiate_validator_exit(
            state, toIntExact(signedExit.getMessage().getValidator_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public static boolean verify_voluntary_exits(
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
