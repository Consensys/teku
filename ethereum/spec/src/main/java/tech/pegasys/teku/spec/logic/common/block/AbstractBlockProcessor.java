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

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.cache.CapturingIndexedAttestationCache;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
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
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.merge.block.OptimisticExecutionPayloadExecutor;

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

  protected final OperationSignatureVerifier operationSignatureVerifier;
  protected final BeaconStateUtil beaconStateUtil;
  protected final AttestationUtil attestationUtil;
  protected final ValidatorsUtil validatorsUtil;
  private final OperationValidator operationValidator;

  protected AbstractBlockProcessor(
      final SpecConfig specConfig,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator) {
    this.specConfig = specConfig;
    this.predicates = predicates;
    this.beaconStateMutators = beaconStateMutators;
    this.operationSignatureVerifier = operationSignatureVerifier;
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.validatorsUtil = validatorsUtil;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.operationValidator = operationValidator;
  }

  @Override
  public BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final BeaconState blockSlotState,
      final IndexedAttestationCache indexedAttestationCache,
      final OptimisticExecutionPayloadExecutor payloadExecutor)
      throws StateTransitionException {
    final BatchSignatureVerifier signatureVerifier = new BatchSignatureVerifier();
    final BeaconState result =
        processAndValidateBlock(
            signedBlock,
            blockSlotState,
            indexedAttestationCache,
            signatureVerifier,
            payloadExecutor);
    if (!signatureVerifier.batchVerify()) {
      throw new StateTransitionException(
          "Batch signature verification failed for block "
              + LogFormatter.formatBlock(signedBlock.getSlot(), signedBlock.getRoot()));
    }
    return result;
  }

  @Override
  public BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final BeaconState blockSlotState,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final OptimisticExecutionPayloadExecutor payloadExecutor)
      throws StateTransitionException {
    try {
      // Process_block
      BeaconState postState =
          processUnsignedBlock(
              blockSlotState,
              signedBlock.getMessage(),
              indexedAttestationCache,
              signatureVerifier,
              payloadExecutor);

      BlockValidationResult blockValidationResult =
          validateBlock(
              blockSlotState, signedBlock, postState, indexedAttestationCache, signatureVerifier);

      if (!blockValidationResult.isValid()) {
        throw new BlockProcessingException(blockValidationResult.getFailureReason());
      }

      return postState;
    } catch (final IllegalArgumentException | BlockProcessingException e) {
      LOG.warn(
          String.format(
              "State transition error while importing block %s (%s)",
              signedBlock.getSlot(), signedBlock.getRoot()),
          e);
      throw new StateTransitionException(e);
    }
  }

  /**
   * Validate the given block. Checks signatures in the block and verifies stateRoot matches
   * postState
   *
   * @param preState The preState to which the block is applied
   * @param block The block being validated
   * @param postState The post state resulting from processing the block on top of the preState
   * @param indexedAttestationCache A cache for calculating indexed attestations
   * @return A block validation result
   */
  @CheckReturnValue
  private BlockValidationResult validateBlock(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BeaconState postState,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier) {
    return BlockValidationResult.allOf(
        () -> verifyBlockSignatures(preState, block, indexedAttestationCache, signatureVerifier),
        () -> validatePostState(postState, block));
  }

  @CheckReturnValue
  private BlockValidationResult verifyBlockSignatures(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier) {
    BeaconBlock blockMessage = block.getMessage();
    BeaconBlockBody blockBody = blockMessage.getBody();

    return BlockValidationResult.allOf(
        () -> verifyBlockSignature(preState, block, signatureVerifier),
        () ->
            verifyAttestationSignatures(
                preState, blockBody.getAttestations(), signatureVerifier, indexedAttestationCache),
        () -> verifyRandao(preState, blockMessage, signatureVerifier),
        () ->
            verifyProposerSlashings(preState, blockBody.getProposerSlashings(), signatureVerifier),
        () -> verifyVoluntaryExits(preState, blockBody.getVoluntaryExits(), signatureVerifier));
  }

  @CheckReturnValue
  private BlockValidationResult verifyBlockSignature(
      final BeaconState state,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier) {
    final int proposerIndex = beaconStateAccessors.getBeaconProposerIndex(state, block.getSlot());
    final Optional<BLSPublicKey> proposerPublicKey =
        beaconStateAccessors.getValidatorPubKey(state, UInt64.valueOf(proposerIndex));
    if (proposerPublicKey.isEmpty()) {
      return BlockValidationResult.failed("Public key not found for validator " + proposerIndex);
    }
    final Bytes signing_root =
        miscHelpers.computeSigningRoot(
            block.getMessage(), getDomain(state, Domain.BEACON_PROPOSER));
    if (!signatureVerifier.verify(proposerPublicKey.get(), signing_root, block.getSignature())) {
      return BlockValidationResult.failed("Invalid block signature: " + block);
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  private Bytes32 getDomain(final BeaconState state, final Bytes4 beaconProposer) {
    return beaconStateAccessors.getDomain(
        state.getForkInfo(), beaconProposer, beaconStateAccessors.getCurrentEpoch(state));
  }

  @CheckReturnValue
  private BlockValidationResult validatePostState(
      final BeaconState postState, final SignedBeaconBlock block) {
    if (!block.getMessage().getStateRoot().equals(postState.hashTreeRoot())) {
      return BlockValidationResult.failed(
          "Block state root does NOT match the calculated state root!\n"
              + "Block state root: "
              + block.getMessage().getStateRoot().toHexString()
              + "New state root: "
              + postState.hashTreeRoot().toHexString());
    } else {
      return BlockValidationResult.SUCCESSFUL;
    }
  }

  @Override
  @CheckReturnValue
  public Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data) {
    return operationValidator.validateAttestationData(state.getFork(), state, data);
  }

  protected void assertAttestationValid(
      final MutableBeaconState state, final Attestation attestation) {
    final AttestationData data = attestation.getData();

    final Optional<OperationInvalidReason> invalidReason = validateAttestation(state, data);
    checkArgument(
        invalidReason.isEmpty(),
        "process_attestations: %s",
        invalidReason.map(OperationInvalidReason::describe).orElse(""));

    IntList committee =
        beaconStateAccessors.getBeaconCommittee(state, data.getSlot(), data.getIndex());
    checkArgument(
        attestation.getAggregationBits().size() == committee.size(),
        "process_attestations: Attestation aggregation bits and committee don't have the same length");
  }

  @Override
  public BeaconState processUnsignedBlock(
      final BeaconState preState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final OptimisticExecutionPayloadExecutor payloadExecutor)
      throws BlockProcessingException {
    return preState.updated(
        state ->
            processBlock(
                state, block, indexedAttestationCache, signatureVerifier, payloadExecutor));
  }

  protected void processBlock(
      final MutableBeaconState state,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final OptimisticExecutionPayloadExecutor payloadExecutor)
      throws BlockProcessingException {
    processBlockHeader(state, block);
    processRandaoNoValidation(state, block.getBody());
    processEth1Data(state, block.getBody());
    processOperationsNoValidation(state, block.getBody(), indexedAttestationCache);
  }

  @Override
  public void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
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
          checkArgument(
              !proposer.isSlashed(), "process_block_header: Verify proposer is not slashed");
        });
  }

  protected void processRandaoNoValidation(MutableBeaconState state, BeaconBlockBody body)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);

          Bytes32 mix =
              beaconStateAccessors
                  .getRandaoMix(state, epoch)
                  .xor(Hash.sha256(body.getRandaoReveal().toSSZBytes()));
          int index = epoch.mod(specConfig.getEpochsPerHistoricalVector()).intValue();
          state.getRandao_mixes().setElement(index, mix);
        });
  }

  protected BlockValidationResult verifyRandao(
      final BeaconState state, final BeaconBlock block, final BLSSignatureVerifier bls) {
    UInt64 epoch = miscHelpers.computeEpochAtSlot(block.getSlot());
    // Verify RANDAO reveal
    final BLSPublicKey proposerPublicKey =
        beaconStateAccessors.getValidatorPubKey(state, block.getProposerIndex()).orElseThrow();
    final Bytes32 domain = getDomain(state, Domain.RANDAO);
    final Bytes signing_root = miscHelpers.computeSigningRoot(epoch, domain);
    if (!bls.verify(proposerPublicKey, signing_root, block.getBody().getRandaoReveal())) {
      return BlockValidationResult.failed("Randao reveal is invalid.");
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  protected void processEth1Data(final MutableBeaconState state, final BeaconBlockBody body) {
    state.getEth1_data_votes().append(body.getEth1Data());
    long vote_count = getVoteCount(state, body.getEth1Data());
    if (isEnoughVotesToUpdateEth1Data(vote_count)) {
      state.setEth1_data(body.getEth1Data());
    }
  }

  @Override
  public boolean isEnoughVotesToUpdateEth1Data(final long voteCount) {
    return voteCount * 2
        > (long) specConfig.getEpochsPerEth1VotingPeriod() * specConfig.getSlotsPerEpoch();
  }

  @Override
  public long getVoteCount(final BeaconState state, final Eth1Data eth1Data) {
    return state.getEth1_data_votes().stream().filter(item -> item.equals(eth1Data)).count();
  }

  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
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

          processProposerSlashingsNoValidation(state, body.getProposerSlashings());
          processAttesterSlashings(state, body.getAttesterSlashings());
          processAttestationsNoVerification(state, body.getAttestations(), indexedAttestationCache);
          processDeposits(state, body.getDeposits());
          processVoluntaryExitsNoValidation(state, body.getVoluntaryExits());
          // @process_shard_receipt_proofs
        });
  }

  @Override
  public void processProposerSlashings(
      final MutableBeaconState state,
      final SszList<ProposerSlashing> proposerSlashings,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    processProposerSlashingsNoValidation(state, proposerSlashings);
    final BlockValidationResult validationResult =
        verifyProposerSlashings(state, proposerSlashings, signatureVerifier);
    if (!validationResult.isValid()) {
      throw new BlockProcessingException("Slashing signature is invalid");
    }
  }

  protected void processProposerSlashingsNoValidation(
      MutableBeaconState state, SszList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          // For each proposer_slashing in block.body.proposer_slashings:
          for (ProposerSlashing proposerSlashing : proposerSlashings) {
            Optional<OperationInvalidReason> invalidReason =
                operationValidator.validateProposerSlashing(
                    state.getFork(), state, proposerSlashing);
            checkArgument(
                invalidReason.isEmpty(),
                "process_proposer_slashings: %s",
                invalidReason.map(OperationInvalidReason::describe).orElse(""));

            beaconStateMutators.slashValidator(
                state,
                toIntExact(
                    proposerSlashing.getHeader_1().getMessage().getProposerIndex().longValue()));
          }
        });
  }

  protected BlockValidationResult verifyProposerSlashings(
      BeaconState state,
      SszList<ProposerSlashing> proposerSlashings,
      BLSSignatureVerifier signatureVerifier) {
    // For each proposer_slashing in block.body.proposer_slashings:
    for (ProposerSlashing proposerSlashing : proposerSlashings) {

      boolean slashingSignatureValid =
          operationSignatureVerifier.verifyProposerSlashingSignature(
              state.getFork(), state, proposerSlashing, signatureVerifier);
      if (!slashingSignatureValid) {
        return BlockValidationResult.failed(
            "Proposer slashing signature is invalid " + proposerSlashing);
      }
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  @Override
  public void processAttesterSlashings(
      MutableBeaconState state, SszList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          // For each attester_slashing in block.body.attester_slashings:
          for (AttesterSlashing attesterSlashing : attesterSlashings) {
            List<UInt64> indicesToSlash = new ArrayList<>();
            final Optional<OperationInvalidReason> invalidReason =
                operationValidator.validateAttesterSlashing(
                    state.getFork(), state, attesterSlashing, indicesToSlash::add);

            checkArgument(
                invalidReason.isEmpty(),
                "process_attester_slashings: %s",
                invalidReason.map(OperationInvalidReason::describe).orElse(""));

            indicesToSlash.forEach(
                indexToSlash ->
                    beaconStateMutators.slashValidator(
                        state, toIntExact(indexToSlash.longValue())));
          }
        });
  }

  @Override
  public void processAttestations(
      final MutableBeaconState state,
      final SszList<Attestation> attestations,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final CapturingIndexedAttestationCache indexedAttestationCache =
        IndexedAttestationCache.capturing();
    processAttestationsNoVerification(state, attestations, indexedAttestationCache);

    final BlockValidationResult result =
        verifyAttestationSignatures(
            state, attestations, signatureVerifier, indexedAttestationCache);
    if (!result.isValid()) {
      throw new BlockProcessingException(result.getFailureReason());
    }
  }

  protected void processAttestationsNoVerification(
      MutableBeaconState state,
      SszList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    final IndexedAttestationProvider indexedAttestationProvider =
        createIndexedAttestationProvider(state, indexedAttestationCache);
    safelyProcess(
        () -> {
          for (Attestation attestation : attestations) {
            // Validate
            assertAttestationValid(state, attestation);
            processAttestation(state, attestation, indexedAttestationProvider);
          }
        });
  }

  private IndexedAttestationProvider createIndexedAttestationProvider(
      BeaconState state, IndexedAttestationCache indexedAttestationCache) {
    return (attestation) ->
        indexedAttestationCache.computeIfAbsent(
            attestation, () -> attestationUtil.getIndexedAttestation(state, attestation));
  }

  /**
   * Corresponds to fork-specific logic from "process_attestation" spec method. Common validation
   * and signature verification logic can be found in {@link
   * #verifyAttestationSignatures(BeaconState, SszList, BLSSignatureVerifier,
   * IndexedAttestationCache)}.
   *
   * @param genericState The state corresponding to the block being processed
   * @param attestation An attestation in the body of the block being processed
   * @param indexedAttestationProvider provider of indexed attestations
   */
  protected abstract void processAttestation(
      final MutableBeaconState genericState,
      final Attestation attestation,
      final IndexedAttestationProvider indexedAttestationProvider);

  @CheckReturnValue
  protected BlockValidationResult verifyAttestationSignatures(
      BeaconState state,
      SszList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationCache indexedAttestationCache) {
    return verifyAttestationSignatures(
        state,
        attestations,
        signatureVerifier,
        createIndexedAttestationProvider(state, indexedAttestationCache));
  }

  @CheckReturnValue
  protected BlockValidationResult verifyAttestationSignatures(
      BeaconState state,
      SszList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationProvider indexedAttestationProvider) {

    Optional<AttestationProcessingResult> processResult =
        attestations.stream()
            .map(indexedAttestationProvider::getIndexedAttestation)
            .map(
                attestation ->
                    attestationUtil.isValidIndexedAttestation(
                        state.getFork(), state, attestation, signatureVerifier))
            .filter(result -> !result.isSuccessful())
            .findAny();
    return processResult
        .map(
            attestationProcessingResult ->
                BlockValidationResult.failed(
                    "Invalid attestation: " + attestationProcessingResult.getInvalidReason()))
        .orElse(BlockValidationResult.SUCCESSFUL);
  }

  @Override
  public void processDeposits(MutableBeaconState state, SszList<? extends Deposit> deposits)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          for (Deposit deposit : deposits) {
            processDeposit(state, deposit);
          }
        });
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
    final Bytes32 domain = miscHelpers.computeDomain(Domain.DEPOSIT);
    final Bytes signing_root = miscHelpers.computeSigningRoot(deposit_message, domain);
    // Note that this can't use batch signature verification as invalid deposits can be included
    // in blocks and processing differs based on whether the signature is valid or not.
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

  @Override
  public void processVoluntaryExits(
      final MutableBeaconState state,
      final SszList<SignedVoluntaryExit> exits,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {

    processVoluntaryExitsNoValidation(state, exits);
    BlockValidationResult signaturesValid =
        verifyVoluntaryExits(state, exits, BLSSignatureVerifier.SIMPLE);
    if (!signaturesValid.isValid()) {
      throw new BlockProcessingException(signaturesValid.getFailureReason());
    }
  }

  protected void processVoluntaryExitsNoValidation(
      MutableBeaconState state, SszList<SignedVoluntaryExit> exits)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          // For each exit in block.body.voluntaryExits:
          for (SignedVoluntaryExit signedExit : exits) {
            Optional<OperationInvalidReason> invalidReason =
                operationValidator.validateVoluntaryExit(state.getFork(), state, signedExit);
            checkArgument(
                invalidReason.isEmpty(),
                "process_voluntary_exits: %s",
                invalidReason.map(OperationInvalidReason::describe).orElse(""));

            // - Run initiate_validator_exit(state, exit.validator_index)
            beaconStateMutators.initiateValidatorExit(
                state, toIntExact(signedExit.getMessage().getValidatorIndex().longValue()));
          }
        });
  }

  protected BlockValidationResult verifyVoluntaryExits(
      BeaconState state,
      SszList<SignedVoluntaryExit> exits,
      BLSSignatureVerifier signatureVerifier) {
    for (SignedVoluntaryExit signedExit : exits) {
      boolean exitSignatureValid =
          operationSignatureVerifier.verifyVoluntaryExitSignature(
              state.getFork(), state, signedExit, signatureVerifier);
      if (!exitSignatureValid) {
        return BlockValidationResult.failed("Exit signature is invalid: " + signedExit);
      }
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  // Catch generic errors and wrap them in a BlockProcessingException
  private void safelyProcess(BlockProcessingAction action) throws BlockProcessingException {
    try {
      action.run();
    } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
      LOG.warn("Failed to process block", e);
      throw new BlockProcessingException(e);
    }
  }

  protected interface IndexedAttestationProvider {
    IndexedAttestation getIndexedAttestation(final Attestation attestation);
  }

  private interface BlockProcessingAction {
    void run() throws BlockProcessingException;
  }
}
