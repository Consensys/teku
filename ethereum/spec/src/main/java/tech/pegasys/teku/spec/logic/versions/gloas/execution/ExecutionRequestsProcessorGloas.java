/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.gloas.execution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionRequestsProcessorGloas extends ExecutionRequestsProcessorElectra {
  private static final Logger LOG = LogManager.getLogger();

  private final SchemaDefinitionsGloas schemaDefinitionsGloas;
  private final SpecConfigGloas specConfigGloas;
  private final MiscHelpersGloas miscHelpersGloas;
  private final PredicatesGloas predicatesGloas;
  private final BeaconStateMutatorsGloas beaconStateMutatorsGloas;
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;

  public ExecutionRequestsProcessorGloas(
      final SchemaDefinitionsGloas schemaDefinitions,
      final MiscHelpersGloas miscHelpers,
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final BeaconStateAccessorsGloas beaconStateAccessors) {
    super(
        schemaDefinitions,
        miscHelpers,
        specConfig,
        predicates,
        validatorsUtil,
        beaconStateMutators,
        beaconStateAccessors);
    this.schemaDefinitionsGloas = schemaDefinitions;
    this.specConfigGloas = specConfig;
    this.miscHelpersGloas = miscHelpers;
    this.predicatesGloas = predicates;
    this.beaconStateMutatorsGloas = beaconStateMutators;
    this.beaconStateAccessorsGloas = beaconStateAccessors;
  }

  // apply_parent_execution_payload
  public void applyParentExecutionPayload(
      final MutableBeaconStateGloas state,
      final ExecutionRequests requests,
      final Supplier<ValidatorExitContext> validatorExitContextSupplier) {
    final ExecutionPayloadBid parentBid = state.getLatestExecutionPayloadBid();
    final UInt64 parentSlot = parentBid.getSlot();
    final UInt64 parentEpoch = miscHelpers.computeEpochAtSlot(parentSlot);

    // Process execution requests from parent's payload. The execution requests are processed at
    // state.slot (child's slot), not the parent's slot.
    final long startTimeNanos = System.nanoTime();
    LOG.debug(
        "Starting processing builder deposits from {} execution request deposits at timestampNanos={}",
        requests.getDeposits().size(),
        startTimeNanos);
    processDepositRequests(state, requests.getDeposits());
    final long finishTimeNanos = System.nanoTime();
    LOG.debug(
        "Finished processing builder deposits at timestampNanos={}. Pending deposits: {}, builders: {}, elapsedNanos={}",
        finishTimeNanos,
        state.getPendingDeposits().size(),
        state.getBuilders().size(),
        finishTimeNanos - startTimeNanos);
    processWithdrawalRequests(state, requests.getWithdrawals(), validatorExitContextSupplier);
    processConsolidationRequests(state, requests.getConsolidations());

    // Settle the builder payment
    if (parentEpoch.equals(beaconStateAccessorsGloas.getCurrentEpoch(state))) {
      final UInt64 paymentIndex =
          parentSlot
              .mod(specConfigGloas.getSlotsPerEpoch())
              .plus(specConfigGloas.getSlotsPerEpoch());
      beaconStateMutatorsGloas.settleBuilderPayment(state, paymentIndex);
    } else if (parentEpoch.equals(beaconStateAccessorsGloas.getPreviousEpoch(state))) {
      final UInt64 paymentIndex = parentSlot.mod(specConfigGloas.getSlotsPerEpoch());
      beaconStateMutatorsGloas.settleBuilderPayment(state, paymentIndex);
    } else if (parentBid.getValue().isGreaterThan(UInt64.ZERO)) {
      // Parent is older than the previous epoch, its payment entry has been
      // evicted from builder_pending_payments. Append the withdrawal directly.
      state
          .getBuilderPendingWithdrawals()
          .append(
              schemaDefinitionsGloas
                  .getBuilderPendingWithdrawalSchema()
                  .create(
                      parentBid.getFeeRecipient(),
                      parentBid.getValue(),
                      parentBid.getBuilderIndex()));
    }

    // Update parent payload availability and latest block hash
    state.setExecutionPayloadAvailability(
        state
            .getExecutionPayloadAvailability()
            .withBit(parentSlot.mod(specConfigGloas.getSlotsPerHistoricalRoot()).intValue()));
    state.setLatestBlockHash(parentBid.getBlockHash());
  }

  @Override
  public void processDepositRequests(
      final MutableBeaconState state, final List<DepositRequest> depositRequests) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);

    final List<DepositRequest> newBuilderDeposits = new ArrayList<>();
    // Avoids re-scanning pending deposits and re-verifying signatures for repeated pubkeys
    final Set<BLSPublicKey> verifiedPendingValidatorPubkeys = new HashSet<>();

    for (final DepositRequest depositRequest : depositRequests) {
      final BLSPublicKey pubkey = depositRequest.getPubkey();
      // Regardless of the withdrawal credentials prefix, if a builder/validator already exists with
      // this pubkey, apply the deposit to their balance
      final boolean isBuilder =
          beaconStateAccessorsGloas.getBuilderIndex(state, pubkey).isPresent();
      final boolean isValidator = validatorsUtil.getValidatorIndex(state, pubkey).isPresent();
      final boolean isPendingValidator =
          verifiedPendingValidatorPubkeys.contains(pubkey)
              || (miscHelpersGloas.isPendingValidator(stateElectra.getPendingDeposits(), pubkey)
                  && verifiedPendingValidatorPubkeys.add(pubkey));
      final boolean isNewBuilderDeposit =
          !isBuilder
              && predicatesGloas.isBuilderWithdrawalCredential(
                  depositRequest.getWithdrawalCredentials())
              && !isValidator
              && !isPendingValidator;

      if (isNewBuilderDeposit) {
        // new builder deposits will be processed at the end so we can batch the signature
        // verifications
        newBuilderDeposits.add(depositRequest);
      } else if (isBuilder) {
        applyDepositForBuilder(state, depositRequest, false);
      } else {
        // Add validator deposits to the queue
        final SszMutableList<PendingDeposit> pendingDeposits = stateElectra.getPendingDeposits();
        final PendingDeposit deposit =
            schemaDefinitions
                .getPendingDepositSchema()
                .create(
                    new SszPublicKey(pubkey),
                    SszBytes32.of(depositRequest.getWithdrawalCredentials()),
                    SszUInt64.of(depositRequest.getAmount()),
                    new SszSignature(depositRequest.getSignature()),
                    SszUInt64.of(state.getSlot()));
        pendingDeposits.append(deposit);
      }
    }

    final boolean newBuilderDepositSignaturesAreAllGood =
        batchVerifyNewBuilderDepositSignatures(newBuilderDeposits);

    for (final DepositRequest newBuilderDeposit : newBuilderDeposits) {
      applyDepositForBuilder(state, newBuilderDeposit, newBuilderDepositSignaturesAreAllGood);
    }
  }

  // Apply builder deposits immediately
  private void applyDepositForBuilder(
      final MutableBeaconState state,
      final DepositRequest depositRequest,
      final boolean signatureAlreadyVerified) {
    beaconStateMutatorsGloas.applyDepositForBuilder(
        state,
        depositRequest.getPubkey(),
        depositRequest.getWithdrawalCredentials(),
        depositRequest.getAmount(),
        depositRequest.getSignature(),
        state.getSlot(),
        signatureAlreadyVerified);
  }

  private boolean batchVerifyNewBuilderDepositSignatures(
      final List<DepositRequest> newBuilderDeposits) {
    try {
      final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
      final List<Bytes> messages = new ArrayList<>();
      final List<BLSSignature> signatures = new ArrayList<>();
      for (final DepositRequest newBuilderDeposit : newBuilderDeposits) {
        final BLSPublicKey pubkey = newBuilderDeposit.getPubkey();
        publicKeys.add(List.of(pubkey));
        messages.add(
            miscHelpers.computeDepositSigningRoot(
                pubkey,
                newBuilderDeposit.getWithdrawalCredentials(),
                newBuilderDeposit.getAmount()));
        signatures.add(newBuilderDeposit.getSignature());
      }
      return specConfig.getBLSSignatureVerifier().verify(publicKeys, messages, signatures);
    } catch (final BlsException e) {
      return false;
    }
  }
}
