/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.block;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSSignatureVerifier.InvalidSignatureException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.signatures.ProposerSlashingSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.signatures.VoluntaryExitSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.ProposerSlashingStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.operations.validation.VoluntaryExitStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.ssz.SszList;

public abstract class AbstractBlockProcessor implements BlockProcessor {
  /**
   * For debug/test purposes only enables/disables {@link DepositData} BLS signature verification
   * Setting to <code>false</code> significantly speeds up state initialization
   */
  public static boolean BLS_VERIFY_DEPOSIT = true;

  private static final Logger LOG = LogManager.getLogger();

  protected final SpecConfig specConfig;
  protected final Predicates predicates;
  protected final MiscHelpers miscHelpers;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final BeaconStateMutators beaconStateMutators;

  protected final BeaconStateUtil beaconStateUtil;
  protected final AttestationUtil attestationUtil;
  protected final ValidatorsUtil validatorsUtil;
  private final AttestationDataStateTransitionValidator attestationValidator;

  protected AbstractBlockProcessor(
      final SpecConfig specConfig,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final AttestationDataStateTransitionValidator attestationValidator) {
    this.specConfig = specConfig;
    this.predicates = predicates;
    this.beaconStateMutators = beaconStateMutators;
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.validatorsUtil = validatorsUtil;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.attestationValidator = attestationValidator;
  }

  @Override
  public Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data) {
    return attestationValidator.validate(state, data);
  }

  protected void assertAttestationValid(
      final MutableBeaconState state, final Attestation attestation) {
    final AttestationData data = attestation.getData();

    final Optional<OperationInvalidReason> invalidReason = validateAttestation(state, data);
    checkArgument(
        invalidReason.isEmpty(),
        "process_attestations: %s",
        invalidReason.map(OperationInvalidReason::describe).orElse(""));

    List<Integer> committee =
        beaconStateUtil.getBeaconCommittee(state, data.getSlot(), data.getIndex());
    checkArgument(
        attestation.getAggregation_bits().size() == committee.size(),
        "process_attestations: Attestation aggregation bits and committee don't have the same length");
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
  @Override
  public void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    try {
      checkArgument(
          blockHeader.getSlot().equals(state.getSlot()),
          "process_block_header: Verify that the slots match");
      checkArgument(
          blockHeader.getProposerIndex().longValue()
              == beaconStateAccessors.getBeaconProposerIndex(state),
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

  @Override
  public void processRandaoNoValidation(MutableBeaconState state, BeaconBlockBody body)
      throws BlockProcessingException {
    try {
      UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);

      Bytes32 mix =
          beaconStateAccessors
              .getRandaoMix(state, epoch)
              .xor(Hash.sha2_256(body.getRandao_reveal().toSSZBytes()));
      int index = epoch.mod(specConfig.getEpochsPerHistoricalVector()).intValue();
      state.getRandao_mixes().setElement(index, mix);
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  @Override
  public void verifyRandao(BeaconState state, BeaconBlock block, BLSSignatureVerifier bls)
      throws InvalidSignatureException {
    UInt64 epoch = miscHelpers.computeEpochAtSlot(block.getSlot());
    // Verify RANDAO reveal
    final BLSPublicKey proposerPublicKey =
        beaconStateAccessors.getValidatorPubKey(state, block.getProposerIndex()).orElseThrow();
    final Bytes32 domain = beaconStateUtil.getDomain(state, specConfig.getDomainRandao());
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
  @Override
  public void processEth1Data(MutableBeaconState state, BeaconBlockBody body) {
    state.getEth1_data_votes().append(body.getEth1_data());
    long vote_count = getVoteCount(state, body.getEth1_data());
    if (isEnoughVotesToUpdateEth1Data(vote_count)) {
      state.setEth1_data(body.getEth1_data());
    }
  }

  @Override
  public boolean isEnoughVotesToUpdateEth1Data(long voteCount) {
    return voteCount * 2
        > (long) specConfig.getEpochsPerEth1VotingPeriod() * specConfig.getSlotsPerEpoch();
  }

  @Override
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
  @Override
  public void processOperationsNoValidation(
      MutableBeaconState state,
      BeaconBlockBody body,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    try {

      checkArgument(
          body.getDeposits().size()
              == Math.min(
                  specConfig.getMaxDeposits(),
                  toIntExact(
                      state
                          .getEth1_data()
                          .getDeposit_count()
                          .minus(state.getEth1_deposit_index())
                          .longValue())),
          "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");

      processProposerSlashingsNoValidation(state, body.getProposer_slashings());
      processAttesterSlashings(state, body.getAttester_slashings());
      processAttestations(state, body.getAttestations(), indexedAttestationCache, false);
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
  @Override
  public void processProposerSlashings(
      MutableBeaconState state, SszList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
    processProposerSlashingsNoValidation(state, proposerSlashings);
    boolean signaturesValid =
        verifyProposerSlashings(state, proposerSlashings, BLSSignatureVerifier.SIMPLE);
    if (!signaturesValid) {
      throw new BlockProcessingException("Slashing signature is invalid");
    }
  }

  @Override
  public void processProposerSlashingsNoValidation(
      MutableBeaconState state, SszList<ProposerSlashing> proposerSlashings)
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

        beaconStateMutators.slashValidator(
            state,
            toIntExact(proposerSlashing.getHeader_1().getMessage().getProposerIndex().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  @Override
  public boolean verifyProposerSlashings(
      BeaconState state,
      SszList<ProposerSlashing> proposerSlashings,
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
  @Override
  public void processAttesterSlashings(
      MutableBeaconState state, SszList<AttesterSlashing> attesterSlashings)
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
                beaconStateMutators.slashValidator(state, toIntExact(indexToSlash.longValue())));
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
  @Override
  public void processAttestations(MutableBeaconState state, SszList<Attestation> attestations)
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
  @Override
  public void processAttestations(
      MutableBeaconState state,
      SszList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    processAttestations(state, attestations, indexedAttestationCache, true);
  }

  protected void processAttestations(
      MutableBeaconState state,
      SszList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache,
      final boolean verifySignatures)
      throws BlockProcessingException {
    final IndexedAttestationProvider indexedAttestationProvider =
        createIndexedAttestationProvider(state, indexedAttestationCache);
    try {
      for (Attestation attestation : attestations) {
        // Validate
        assertAttestationValid(state, attestation);
        processAttestation(state, attestation, indexedAttestationProvider);
        if (verifySignatures) {
          verifyAttestationSignatures(
              state, attestations, BLSSignatureVerifier.SIMPLE, indexedAttestationProvider);
        }
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  private IndexedAttestationProvider createIndexedAttestationProvider(
      BeaconState state, IndexedAttestationCache indexedAttestationCache) {
    return (attestation) ->
        indexedAttestationCache.computeIfAbsent(
            attestation, () -> attestationUtil.getIndexedAttestation(state, attestation));
  }

  /**
   * Corresponds to fork-specific logic from "process_attestation" spec method. Common validation
   * and signature verification logic can be found in {@link #processAttestations}.
   *
   * @param genericState The state corresponding to the block being processed
   * @param attestation An attestation in the body of the block being processed
   * @param indexedAttestationProvider
   */
  protected abstract void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider);

  @Override
  public void verifyAttestationSignatures(
      BeaconState state,
      SszList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    verifyAttestationSignatures(
        state,
        attestations,
        signatureVerifier,
        createIndexedAttestationProvider(state, indexedAttestationCache));
  }

  protected void verifyAttestationSignatures(
      BeaconState state,
      SszList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationProvider indexedAttestationProvider)
      throws BlockProcessingException {

    Optional<AttestationProcessingResult> processResult =
        attestations.stream()
            .map(indexedAttestationProvider::getIndexedAttestation)
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
  @Override
  public void processDeposits(MutableBeaconState state, SszList<? extends Deposit> deposits)
      throws BlockProcessingException {
    try {
      for (Deposit deposit : deposits) {
        processDeposit(state, deposit);
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  public void processDeposit(MutableBeaconState state, Deposit deposit) {
    checkArgument(
        predicates.isValidMerkleBranch(
            deposit.getData().hashTreeRoot(),
            deposit.getProof(),
            specConfig.getDepositContractTreeDepth() + 1, // Add 1 for the List length mix-in
            toIntExact(state.getEth1_deposit_index().longValue()),
            state.getEth1_data().getDeposit_root()),
        "process_deposit: Verify the Merkle branch");

    processDepositWithoutCheckingMerkleProof(state, deposit, null);
  }

  @Override
  public void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Map<BLSPublicKey, Integer> pubKeyToIndexMap) {
    final BLSPublicKey pubkey = deposit.getData().getPubkey();

    state.setEth1_deposit_index(state.getEth1_deposit_index().plus(UInt64.ONE));

    // Find the validator index associated with this deposit, if it exists
    OptionalInt existingIndex;
    if (pubKeyToIndexMap != null) {
      final Integer cachedIndex =
          pubKeyToIndexMap.putIfAbsent(pubkey, state.getValidators().size());
      existingIndex = cachedIndex == null ? OptionalInt.empty() : OptionalInt.of(cachedIndex);
    } else {
      Function<Integer, BLSPublicKey> validatorPubkey =
          index ->
              beaconStateAccessors.getValidatorPubKey(state, UInt64.valueOf(index)).orElse(null);
      existingIndex =
          IntStream.range(0, state.getValidators().size())
              .filter(index -> pubkey.equals(validatorPubkey.apply(index)))
              .findFirst();
    }

    if (existingIndex.isEmpty()) {
      // This is a new validator
      // Verify the deposit signature (proof of possession) which is not checked by the deposit
      // contract
      if (depositSignatureIsValid(deposit, pubkey)) {
        processNewValidator(state, deposit);
      } else {
        handleInvalidDeposit(deposit, pubkey, pubKeyToIndexMap);
      }
    } else {
      // This validator already exists, increase their balance
      beaconStateMutators.increaseBalance(
          state, existingIndex.getAsInt(), deposit.getData().getAmount());
    }
  }

  private void handleInvalidDeposit(
      final Deposit deposit,
      BLSPublicKey pubkey,
      final Map<BLSPublicKey, Integer> pubKeyToIndexMap) {
    if (deposit instanceof DepositWithIndex) {
      LOG.debug(
          "Skipping invalid deposit with index {} and pubkey {}",
          ((DepositWithIndex) deposit).getIndex(),
          pubkey);
    } else {
      LOG.debug("Skipping invalid deposit with pubkey {}", pubkey);
    }
    if (pubKeyToIndexMap != null) {
      // The validator won't be created so the calculated index won't be correct
      pubKeyToIndexMap.remove(pubkey);
    }
  }

  private boolean depositSignatureIsValid(final Deposit deposit, BLSPublicKey pubkey) {
    if (!BLS_VERIFY_DEPOSIT) {
      return true;
    }

    final UInt64 amount = deposit.getData().getAmount();
    final DepositMessage deposit_message =
        new DepositMessage(pubkey, deposit.getData().getWithdrawal_credentials(), amount);
    final Bytes32 domain = beaconStateUtil.computeDomain(specConfig.getDomainDeposit());
    final Bytes signing_root = beaconStateUtil.computeSigningRoot(deposit_message, domain);
    return BLS.verify(pubkey, signing_root, deposit.getData().getSignature());
  }

  protected void processNewValidator(final MutableBeaconState state, final Deposit deposit) {
    LOG.debug("Adding new validator with index {} to state", state.getValidators().size());
    state.getValidators().append(getValidatorFromDeposit(deposit));
    state.getBalances().appendElement(deposit.getData().getAmount());
  }

  private Validator getValidatorFromDeposit(Deposit deposit) {
    final UInt64 amount = deposit.getData().getAmount();
    final UInt64 effectiveBalance =
        amount
            .minus(amount.mod(specConfig.getEffectiveBalanceIncrement()))
            .min(specConfig.getMaxEffectiveBalance());
    return new Validator(
        deposit.getData().getPubkey(),
        deposit.getData().getWithdrawal_credentials(),
        effectiveBalance,
        false,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH);
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
  @Override
  public void processVoluntaryExits(MutableBeaconState state, SszList<SignedVoluntaryExit> exits)
      throws BlockProcessingException {

    processVoluntaryExitsNoValidation(state, exits);
    boolean signaturesValid = verifyVoluntaryExits(state, exits, BLSSignatureVerifier.SIMPLE);
    if (!signaturesValid) {
      throw new BlockProcessingException("Exit signature is invalid");
    }
  }

  @Override
  public void processVoluntaryExitsNoValidation(
      MutableBeaconState state, SszList<SignedVoluntaryExit> exits)
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
        beaconStateMutators.initiateValidatorExit(
            state, toIntExact(signedExit.getMessage().getValidator_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  @Override
  public boolean verifyVoluntaryExits(
      BeaconState state,
      SszList<SignedVoluntaryExit> exits,
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

  protected interface IndexedAttestationProvider {
    IndexedAttestation getIndexedAttestation(final Attestation attestation);
  }
}
