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

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.versions.fulu.execution.ExecutionRequestsProcessorFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionRequestsProcessorGloas extends ExecutionRequestsProcessorFulu {

  // New builder deposit signatures are checked with BLS batch verification, which only returns a
  // single pass/fail for the whole batch. A failure does not reveal which signature is invalid, so
  // a single bad signature forces a fallback to verify every signature in the batch one by one.
  // Capping the batch at this size bounds that cost: a bad signature only triggers individual
  // re-verification within its own sub-batch rather than across all new builder deposits.
  private static final int NEW_BUILDER_DEPOSIT_SIGNATURE_VERIFICATION_BATCH_SIZE = 256;

  private final MiscHelpersGloas miscHelpersGloas;
  private final PredicatesGloas predicatesGloas;
  private final BeaconStateMutatorsGloas beaconStateMutatorsGloas;
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;

  public ExecutionRequestsProcessorGloas(
      final SchemaDefinitionsGloas schemaDefinitions,
      final MiscHelpersGloas miscHelpers,
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final ValidatorsUtilGloas validatorsUtil,
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
    this.miscHelpersGloas = miscHelpers;
    this.predicatesGloas = predicates;
    this.beaconStateMutatorsGloas = beaconStateMutators;
    this.beaconStateAccessorsGloas = beaconStateAccessors;
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
      final boolean isNewBuilderDeposit =
          !isBuilder
              && predicatesGloas.isBuilderWithdrawalCredential(
                  depositRequest.getWithdrawalCredentials())
              // not is_validator
              && validatorsUtil.getValidatorIndex(state, pubkey).isEmpty()
              // not is_pending_validator
              && !(verifiedPendingValidatorPubkeys.contains(pubkey)
                  || (miscHelpersGloas.isPendingValidator(
                          stateElectra.getPendingDeposits().asList(), pubkey)
                      && verifiedPendingValidatorPubkeys.add(pubkey)));

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

    Lists.partition(newBuilderDeposits, NEW_BUILDER_DEPOSIT_SIGNATURE_VERIFICATION_BATCH_SIZE)
        .forEach(
            newBuilderDepositsBatch -> {
              final boolean newBuilderDepositSignaturesAreAllGood =
                  batchVerifyNewBuilderDepositSignatures(newBuilderDepositsBatch);
              for (final DepositRequest newBuilderDeposit : newBuilderDepositsBatch) {
                applyDepositForBuilder(
                    state, newBuilderDeposit, newBuilderDepositSignaturesAreAllGood);
              }
            });
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
