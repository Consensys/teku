/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
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
import tech.pegasys.teku.spec.datastructures.consolidations.SignedConsolidation;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerWithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
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
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public abstract class AbstractBlockProcessor implements BlockProcessor {

  @VisibleForTesting
  public static final BLSSignatureVerifier DEFAULT_DEPOSIT_SIGNATURE_VERIFIER =
      BLSSignatureVerifier.SIMPLE;

  /**
   * For debug/test purposes only enables/disables {@link DepositData} BLS signature verification
   * Setting to <code>false</code> significantly speeds up state initialization
   */
  @VisibleForTesting
  public static BLSSignatureVerifier depositSignatureVerifier = DEFAULT_DEPOSIT_SIGNATURE_VERIFIER;

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
  protected final OperationValidator operationValidator;

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
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
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
          "Batch signature verification failed for block " + signedBlock.toLogString());
    }
    return result;
  }

  @Override
  public BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final BeaconState blockSlotState,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException {
    try {
      final BlockValidationResult preValidationResult =
          validateBlockPreProcessing(blockSlotState, signedBlock, signatureVerifier);
      if (!preValidationResult.isValid()) {
        throw new BlockProcessingException(preValidationResult.getFailureReason());
      }

      // Process_block
      BeaconState postState =
          processUnsignedBlock(
              blockSlotState,
              signedBlock.getMessage(),
              indexedAttestationCache,
              signatureVerifier,
              payloadExecutor);

      BlockValidationResult blockValidationResult =
          validateBlockPostProcessing(
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

  @CheckReturnValue
  protected BlockValidationResult validateBlockPreProcessing(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    return BlockValidationResult.SUCCESSFUL;
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
  private BlockValidationResult validateBlockPostProcessing(
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
  protected BlockValidationResult verifyBlockSignatures(
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
    final Bytes signingRoot =
        miscHelpers.computeSigningRoot(
            block.getMessage(), getDomain(state, Domain.BEACON_PROPOSER));
    if (!signatureVerifier.verify(proposerPublicKey.get(), signingRoot, block.getSignature())) {
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
              + "\n  New state root: "
              + postState.hashTreeRoot().toHexString()
              + "\n block proposer: "
              + block.getProposerIndex());
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
        "process_attestations: Attestation aggregation bits and committee don't have the same length - committee "
            + committee.size()
            + ", aggregation bits "
            + attestation.getAggregationBits().size());
  }

  @Override
  public BeaconState processUnsignedBlock(
      final BeaconState preState,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    return preState.updated(
        state -> {
          processBlock(state, block, indexedAttestationCache, signatureVerifier, payloadExecutor);
          BeaconStateCache.getSlotCaches(state).onBlockProcessed();
        });
  }

  protected void processBlock(
      final MutableBeaconState state,
      final BeaconBlock block,
      final IndexedAttestationCache indexedAttestationCache,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws BlockProcessingException {
    processBlockHeader(state, block);
    processRandaoNoValidation(state, block.getBody());
    processEth1Data(state, block.getBody());
    processOperationsNoValidation(state, block.getBody(), indexedAttestationCache);
  }

  @Override
  public void processBlockHeader(
      final MutableBeaconState state, final BeaconBlockSummary blockHeader)
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
              blockHeader.getParentRoot().equals(state.getLatestBlockHeader().hashTreeRoot()),
              "process_block_header: Verify that the parent matches");
          checkArgument(
              blockHeader.getSlot().compareTo(state.getLatestBlockHeader().getSlot()) > 0,
              "process_block_header: Verify that the block is newer than latest block header");

          // Cache the current block as the new latest block
          state.setLatestBlockHeader(
              new BeaconBlockHeader(
                  blockHeader.getSlot(),
                  blockHeader.getProposerIndex(),
                  blockHeader.getParentRoot(),
                  Bytes32.ZERO, // Overwritten in the next `process_slot` call
                  blockHeader.getBodyRoot()));

          // Only if we are processing blocks (not proposing them)
          final Validator proposer =
              state.getValidators().get(blockHeader.getProposerIndex().intValue());
          checkArgument(
              !proposer.isSlashed(), "process_block_header: Verify proposer is not slashed");
        });
  }

  protected void processRandaoNoValidation(
      final MutableBeaconState state, final BeaconBlockBody body) throws BlockProcessingException {
    safelyProcess(
        () -> {
          final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);

          final Bytes32 mix =
              beaconStateAccessors
                  .getRandaoMix(state, epoch)
                  .xor(Hash.sha256(body.getRandaoReveal().toSSZBytes()));
          final int index = epoch.mod(specConfig.getEpochsPerHistoricalVector()).intValue();
          state.getRandaoMixes().setElement(index, mix);
        });
  }

  protected BlockValidationResult verifyRandao(
      final BeaconState state, final BeaconBlock block, final BLSSignatureVerifier bls) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(block.getSlot());
    // Verify RANDAO reveal
    final BLSPublicKey proposerPublicKey =
        beaconStateAccessors.getValidatorPubKey(state, block.getProposerIndex()).orElseThrow();
    final Bytes32 domain = getDomain(state, Domain.RANDAO);
    final Bytes signingRoot = miscHelpers.computeSigningRoot(epoch, domain);
    if (!bls.verify(proposerPublicKey, signingRoot, block.getBody().getRandaoReveal())) {
      return BlockValidationResult.failed("Randao reveal is invalid.");
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  protected void processEth1Data(final MutableBeaconState state, final BeaconBlockBody body) {
    state.getEth1DataVotes().append(body.getEth1Data());
    final long voteCount = getVoteCount(state, body.getEth1Data());
    if (isEnoughVotesToUpdateEth1Data(voteCount)) {
      state.setEth1Data(body.getEth1Data());
    }
  }

  @Override
  public boolean isEnoughVotesToUpdateEth1Data(final long voteCount) {
    return voteCount * 2
        > (long) specConfig.getEpochsPerEth1VotingPeriod() * specConfig.getSlotsPerEpoch();
  }

  @Override
  public long getVoteCount(final BeaconState state, final Eth1Data eth1Data) {
    return state.getEth1DataVotes().stream().filter(item -> item.equals(eth1Data)).count();
  }

  protected void processOperationsNoValidation(
      final MutableBeaconState state,
      final BeaconBlockBody body,
      final IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          verifyOutstandingDepositsAreProcessed(state, body);

          final Supplier<ValidatorExitContext> validatorExitContextSupplier =
              beaconStateMutators.createValidatorExitContextSupplier(state);

          processProposerSlashingsNoValidation(
              state, body.getProposerSlashings(), validatorExitContextSupplier);
          processAttesterSlashings(
              state, body.getAttesterSlashings(), validatorExitContextSupplier);
          processAttestationsNoVerification(state, body.getAttestations(), indexedAttestationCache);
          processDeposits(state, body.getDeposits());
          processVoluntaryExitsNoValidation(
              state, body.getVoluntaryExits(), validatorExitContextSupplier);
          processExecutionLayerWithdrawalRequests(
              state, body.getOptionalExecutionPayload(), validatorExitContextSupplier);
        });
  }

  protected void verifyOutstandingDepositsAreProcessed(
      final BeaconState state, final BeaconBlockBody body) {
    final int expectedDepositCount =
        Math.min(
            specConfig.getMaxDeposits(),
            state.getEth1Data().getDepositCount().minus(state.getEth1DepositIndex()).intValue());

    checkArgument(
        body.getDeposits().size() == expectedDepositCount,
        "process_operations: Verify that outstanding deposits are processed up to the maximum number of deposits");
  }

  @Override
  public void processProposerSlashings(
      final MutableBeaconState state,
      final SszList<ProposerSlashing> proposerSlashings,
      final BLSSignatureVerifier signatureVerifier)
      throws BlockProcessingException {
    final Supplier<ValidatorExitContext> validatorExitContextSupplier =
        beaconStateMutators.createValidatorExitContextSupplier(state);
    processProposerSlashingsNoValidation(state, proposerSlashings, validatorExitContextSupplier);
    final BlockValidationResult validationResult =
        verifyProposerSlashings(state, proposerSlashings, signatureVerifier);
    if (!validationResult.isValid()) {
      throw new BlockProcessingException("Slashing signature is invalid");
    }
  }

  protected void processProposerSlashingsNoValidation(
      final MutableBeaconState state,
      final SszList<ProposerSlashing> proposerSlashings,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
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
                proposerSlashing.getHeader1().getMessage().getProposerIndex().intValue(),
                validatorExitContextSupplier);
          }
        });
  }

  protected BlockValidationResult verifyProposerSlashings(
      final BeaconState state,
      final SszList<ProposerSlashing> proposerSlashings,
      final BLSSignatureVerifier signatureVerifier) {
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
      final MutableBeaconState state, final SszList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          final Supplier<ValidatorExitContext> validatorExitContextSupplier =
              beaconStateMutators.createValidatorExitContextSupplier(state);
          processAttesterSlashings(state, attesterSlashings, validatorExitContextSupplier);
        });
  }

  @Override
  public void processAttesterSlashings(
      final MutableBeaconState state,
      final SszList<AttesterSlashing> attesterSlashings,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
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
                        state, indexToSlash.intValue(), validatorExitContextSupplier));
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
      final MutableBeaconState state,
      final SszList<Attestation> attestations,
      final IndexedAttestationCache indexedAttestationCache)
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

  public IndexedAttestationProvider createIndexedAttestationProvider(
      final BeaconState state, final IndexedAttestationCache indexedAttestationCache) {
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
      final BeaconState state,
      final SszList<Attestation> attestations,
      final BLSSignatureVerifier signatureVerifier,
      final IndexedAttestationCache indexedAttestationCache) {
    return verifyAttestationSignatures(
        state,
        attestations,
        signatureVerifier,
        createIndexedAttestationProvider(state, indexedAttestationCache));
  }

  @CheckReturnValue
  protected BlockValidationResult verifyAttestationSignatures(
      final BeaconState state,
      final SszList<Attestation> attestations,
      final BLSSignatureVerifier signatureVerifier,
      final IndexedAttestationProvider indexedAttestationProvider) {

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
  public void processDeposits(final MutableBeaconState state, final SszList<Deposit> deposits)
      throws BlockProcessingException {
    safelyProcess(
        () -> {
          final boolean depositSignaturesAreAllGood = batchVerifyDepositSignatures(deposits);
          for (Deposit deposit : deposits) {
            processDeposit(state, deposit, depositSignaturesAreAllGood);
          }
        });
  }

  private boolean batchVerifyDepositSignatures(final SszList<Deposit> deposits) {
    try {
      final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
      final List<Bytes> messages = new ArrayList<>();
      final List<BLSSignature> signatures = new ArrayList<>();
      for (final Deposit deposit : deposits) {
        final BLSPublicKey pubkey = deposit.getData().getPubkey();
        publicKeys.add(List.of(pubkey));
        messages.add(
            computeDepositSigningRoot(
                pubkey,
                deposit.getData().getWithdrawalCredentials(),
                deposit.getData().getAmount()));
        signatures.add(deposit.getData().getSignature());
      }
      // Overwhelmingly often we expect all the deposit signatures to be good
      return depositSignatureVerifier.verify(publicKeys, messages, signatures);
    } catch (final BlsException e) {
      return false;
    }
  }

  public void processDeposit(
      final MutableBeaconState state,
      final Deposit deposit,
      final boolean signatureAlreadyVerified) {
    checkArgument(
        predicates.isValidMerkleBranch(
            deposit.getData().hashTreeRoot(),
            deposit.getProof(),
            specConfig.getDepositContractTreeDepth() + 1, // Add 1 for the List length mix-in
            state.getEth1DepositIndex().intValue(),
            state.getEth1Data().getDepositRoot()),
        "process_deposit: Verify the Merkle branch");

    processDepositWithoutCheckingMerkleProof(
        state, deposit, Optional.empty(), signatureAlreadyVerified);
  }

  @Override
  public void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {

    state.setEth1DepositIndex(state.getEth1DepositIndex().plus(UInt64.ONE));

    applyDeposit(
        state,
        deposit.getData().getPubkey(),
        deposit.getData().getWithdrawalCredentials(),
        deposit.getData().getAmount(),
        deposit.getData().getSignature(),
        maybePubkeyToIndexMap,
        signatureAlreadyVerified);
  }

  public void applyDeposit(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {
    // Find the validator index associated with this deposit, if it exists
    final Optional<Integer> existingIndex =
        maybePubkeyToIndexMap
            .flatMap(
                pubkeyToIndexMap -> {
                  if (pubkeyToIndexMap.containsKey(pubkey)) {
                    return Optional.of(pubkeyToIndexMap.getInt(pubkey));
                  } else {
                    pubkeyToIndexMap.put(pubkey, state.getValidators().size());
                    return Optional.empty();
                  }
                })
            .or(() -> validatorsUtil.getValidatorIndex(state, pubkey));

    if (existingIndex.isEmpty()) {
      // This is a new validator
      // Verify the deposit signature (proof of possession) which is not checked by the deposit
      // contract
      if (signatureAlreadyVerified
          || depositSignatureIsValid(pubkey, withdrawalCredentials, amount, signature)) {
        addValidatorToRegistry(state, pubkey, withdrawalCredentials, amount);
      } else {
        handleInvalidDeposit(pubkey, maybePubkeyToIndexMap);
      }
    } else {
      applyDepositToValidatorIndex(
          state,
          withdrawalCredentials,
          signatureAlreadyVerified,
          existingIndex.get(),
          amount,
          pubkey,
          signature);
    }
  }

  protected void applyDepositToValidatorIndex(
      final MutableBeaconState state,
      final Bytes32 withdrawalCredentials,
      final boolean signatureAlreadyVerified,
      final int validatorIndex,
      final UInt64 amount,
      final BLSPublicKey pubkey,
      final BLSSignature signature) {
    beaconStateMutators.increaseBalance(state, validatorIndex, amount);
  }

  private void handleInvalidDeposit(
      final BLSPublicKey pubkey,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap) {
    LOG.debug("Skipping invalid deposit with pubkey {}", pubkey);
    maybePubkeyToIndexMap.ifPresent(
        pubkeyToIndexMap -> {
          // The validator won't be created so the calculated index won't be correct
          pubkeyToIndexMap.removeInt(pubkey);
        });
  }

  protected boolean depositSignatureIsValid(
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature) {
    try {
      return depositSignatureVerifier.verify(
          pubkey, computeDepositSigningRoot(pubkey, withdrawalCredentials, amount), signature);
    } catch (final BlsException e) {
      return false;
    }
  }

  private Bytes computeDepositSigningRoot(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    final Bytes32 domain = miscHelpers.computeDomain(Domain.DEPOSIT);
    final DepositMessage depositMessage = new DepositMessage(pubkey, withdrawalCredentials, amount);
    return miscHelpers.computeSigningRoot(depositMessage, domain);
  }

  protected void addValidatorToRegistry(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount) {
    final Validator validator = getValidatorFromDeposit(pubkey, withdrawalCredentials, amount);
    LOG.debug("Adding new validator with index {} to state", state.getValidators().size());
    state.getValidators().append(validator);
    state.getBalances().appendElement(amount);
  }

  protected Validator getValidatorFromDeposit(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    final UInt64 effectiveBalance =
        amount
            .minus(amount.mod(specConfig.getEffectiveBalanceIncrement()))
            .min(specConfig.getMaxEffectiveBalance());
    return new Validator(
        pubkey,
        withdrawalCredentials,
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
    final Supplier<ValidatorExitContext> validatorExitContextSupplier =
        beaconStateMutators.createValidatorExitContextSupplier(state);
    processVoluntaryExitsNoValidation(state, exits, validatorExitContextSupplier);
    BlockValidationResult signaturesValid = verifyVoluntaryExits(state, exits, signatureVerifier);
    if (!signaturesValid.isValid()) {
      throw new BlockProcessingException(signaturesValid.getFailureReason());
    }
  }

  protected void processVoluntaryExitsNoValidation(
      final MutableBeaconState state,
      final SszList<SignedVoluntaryExit> exits,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {

    safelyProcess(
        () -> {
          // For each exit in block.body.voluntaryExits:
          for (SignedVoluntaryExit signedExit : exits) {
            final Optional<OperationInvalidReason> invalidReason =
                operationValidator.validateVoluntaryExit(state.getFork(), state, signedExit);
            checkArgument(
                invalidReason.isEmpty(),
                "process_voluntary_exits: %s",
                invalidReason.map(OperationInvalidReason::describe).orElse(""));

            // - Run initiate_validator_exit(state, exit.validator_index)

            beaconStateMutators.initiateValidatorExit(
                state,
                signedExit.getMessage().getValidatorIndex().intValue(),
                validatorExitContextSupplier);
          }
        });
  }

  protected BlockValidationResult verifyVoluntaryExits(
      final BeaconState state,
      final SszList<SignedVoluntaryExit> exits,
      final BLSSignatureVerifier signatureVerifier) {
    for (SignedVoluntaryExit signedExit : exits) {
      final boolean exitSignatureValid =
          operationSignatureVerifier.verifyVoluntaryExitSignature(
              state, signedExit, signatureVerifier);
      if (!exitSignatureValid) {
        return BlockValidationResult.failed("Exit signature is invalid: " + signedExit);
      }
    }
    return BlockValidationResult.SUCCESSFUL;
  }

  protected void processExecutionLayerWithdrawalRequests(
      final MutableBeaconState state,
      final Optional<ExecutionPayload> executionPayload,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    // No ExecutionLayerWithdrawalRequests until Electra
  }

  @Override
  public void processDepositReceipts(
      final MutableBeaconState state, final SszList<DepositReceipt> depositReceipts)
      throws BlockProcessingException {
    // No DepositReceipts until Electra
  }

  @Override
  public void processExecutionLayerWithdrawalRequests(
      final MutableBeaconState state,
      final SszList<ExecutionLayerWithdrawalRequest> withdrawalRequests,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier)
      throws BlockProcessingException {
    // No ExecutionLayerWithdrawalRequests until Electra
  }

  @Override
  public void processConsolidations(
      final MutableBeaconState state, final SszList<SignedConsolidation> consolidations)
      throws BlockProcessingException {
    // No Consolidations until Electra
  }

  // Catch generic errors and wrap them in a BlockProcessingException
  protected void safelyProcess(final BlockProcessingAction action) throws BlockProcessingException {
    try {
      action.run();
    } catch (ArithmeticException | IllegalArgumentException | IndexOutOfBoundsException e) {
      LOG.warn("Failed to process block", e);
      throw new BlockProcessingException(e);
    }
  }

  protected void assertCondition(final boolean condition, final String errorMessage)
      throws BlockProcessingException {
    if (!condition) {
      throw new BlockProcessingException(errorMessage);
    }
  }

  public interface IndexedAttestationProvider {

    IndexedAttestation getIndexedAttestation(final Attestation attestation);
  }

  protected interface BlockProcessingAction {

    void run() throws BlockProcessingException;
  }
}
