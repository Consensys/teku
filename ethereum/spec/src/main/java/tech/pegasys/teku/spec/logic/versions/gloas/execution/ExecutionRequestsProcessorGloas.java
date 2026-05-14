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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionRequestsProcessorGloas extends ExecutionRequestsProcessorElectra {
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
    this.miscHelpersGloas = miscHelpers;
    this.predicatesGloas = predicates;
    this.beaconStateMutatorsGloas = beaconStateMutators;
    this.beaconStateAccessorsGloas = beaconStateAccessors;
  }

  @Override
  public void processDepositRequests(
      final MutableBeaconState state, final List<DepositRequest> depositRequests) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);

    final List<ClassifiedDepositRequest> classifiedDepositRequests = new ArrayList<>();
    final List<DepositRequest> newBuilderDeposits = new ArrayList<>();

    for (final DepositRequest depositRequest : depositRequests) {
      final boolean isValidator =
          validatorsUtil.getValidatorIndex(state, depositRequest.getPubkey()).isPresent();

      final boolean isNewBuilderDeposit =
          predicatesGloas.isBuilderWithdrawalCredential(depositRequest.getWithdrawalCredentials())
              && !isValidator
              && !miscHelpersGloas.isPendingValidator(state, depositRequest.getPubkey());

      classifiedDepositRequests.add(
          new ClassifiedDepositRequest(depositRequest, isNewBuilderDeposit));

      if (isNewBuilderDeposit) {
        newBuilderDeposits.add(depositRequest);
      }
    }

    final boolean newBuilderDepositSignaturesAreAllGood =
        batchVerifyNewBuilderDepositSignatures(newBuilderDeposits);

    for (final ClassifiedDepositRequest depositRequest : classifiedDepositRequests) {
      final boolean signatureAlreadyVerified =
          depositRequest.isNewBuilderDeposit() && newBuilderDepositSignaturesAreAllGood;
      processDepositRequest(
          stateElectra,
          depositRequest.depositRequest(),
          depositRequest.isNewBuilderDeposit(),
          signatureAlreadyVerified);
    }
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

  @Override
  protected void processDepositRequest(
      final MutableBeaconStateElectra state,
      final DepositRequest depositRequest,
      final boolean isNewBuilderDeposit,
      final boolean signatureAlreadyVerified) {
    // Regardless of the withdrawal credentials prefix, if a builder/validator already exists with
    // this pubkey, apply the deposit to their balance
    final boolean isBuilder =
        beaconStateAccessorsGloas.getBuilderIndex(state, depositRequest.getPubkey()).isPresent();
    if (isBuilder || isNewBuilderDeposit) {
      // Apply builder deposits immediately
      beaconStateMutatorsGloas.applyDepositForBuilder(
          state,
          depositRequest.getPubkey(),
          depositRequest.getWithdrawalCredentials(),
          depositRequest.getAmount(),
          depositRequest.getSignature(),
          state.getSlot(),
          signatureAlreadyVerified);
      return;
    }
    // Add validator deposits to the queue
    final SszMutableList<PendingDeposit> pendingDeposits = state.getPendingDeposits();
    final PendingDeposit deposit =
        schemaDefinitions
            .getPendingDepositSchema()
            .create(
                new SszPublicKey(depositRequest.getPubkey()),
                SszBytes32.of(depositRequest.getWithdrawalCredentials()),
                SszUInt64.of(depositRequest.getAmount()),
                new SszSignature(depositRequest.getSignature()),
                SszUInt64.of(state.getSlot()));
    pendingDeposits.append(deposit);
  }

  private record ClassifiedDepositRequest(
      DepositRequest depositRequest, boolean isNewBuilderDeposit) {}
}
