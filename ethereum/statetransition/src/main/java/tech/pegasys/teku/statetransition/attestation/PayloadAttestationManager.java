/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.attestation;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessage;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.PayloadAttestationValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PayloadAttestationManager {
  private static final Logger LOG = LogManager.getLogger();

  private final PayloadAttestationValidator payloadAttestationValidator;
  private final ForkChoice forkChoice;

  public PayloadAttestationManager(
      final PayloadAttestationValidator payloadAttestationValidator, final ForkChoice forkChoice) {
    this.payloadAttestationValidator = payloadAttestationValidator;
    this.forkChoice = forkChoice;
  }

  @SuppressWarnings("unused")
  public SafeFuture<InternalValidationResult> addPayloadAttestation(
      final PayloadAttestationMessage payloadAttestationMessage,
      final Optional<UInt64> arrivalTime) {
    final SafeFuture<InternalValidationResult> validationResult =
        payloadAttestationValidator.validate(payloadAttestationMessage);
    processInternallyPayloadAttestation(validationResult, payloadAttestationMessage);
    return validationResult;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void processInternallyPayloadAttestation(
      final SafeFuture<InternalValidationResult> validationResult,
      final PayloadAttestationMessage payloadAttestationMessage) {
    validationResult.thenAccept(
        internalValidationResult -> {
          if (internalValidationResult.code().equals(ValidationResultCode.ACCEPT)
              || internalValidationResult.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
            forkChoice
                .onPayloadAttestationMessage(payloadAttestationMessage)
                .finish(err -> LOG.error("Failed to process received payload attestation.", err));
          }
        });
  }
}
