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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;

import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderEip7732;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip7732.BeaconStateEip7732;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionPayloadValidator {

  private final Spec spec;
  private final RecentChainData recentChainData;

  private final Set<BlockRootAndBuilderIndex> receivedValidEnvelopeInfoSet =
      LimitedSet.createSynchronized(VALID_BLOCK_SET_SIZE);

  public ExecutionPayloadValidator(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope) {
    final ExecutionPayloadEnvelope envelope = signedExecutionPayloadEnvelope.getMessage();
    /*
     * [IGNORE] The envelope's block root envelope.block_root has been seen (via both gossip and non-gossip sources) (a client MAY queue payload for processing once the block is retrieved).
     */
    if (!recentChainData.containsBlock(envelope.getBeaconBlockRoot())) {
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }
    /*
     * [IGNORE] The node has not seen another valid SignedExecutionPayloadEnvelope for this block root from this builder.
     */
    final BlockRootAndBuilderIndex blockRootAndBuilderIndex =
        new BlockRootAndBuilderIndex(envelope.getBeaconBlockRoot(), envelope.getBuilderIndex());
    if (receivedValidEnvelopeInfoSet.contains(blockRootAndBuilderIndex)) {
      return completedFuture(InternalValidationResult.IGNORE);
    }

    return recentChainData
        .retrieveBlockState(envelope.getBeaconBlockRoot())
        .thenApply(
            maybeState -> {
              /*
               * [REJECT] block passes validation.
               * If state is not available for a block root, block has failed validation
               */
              if (maybeState.isEmpty()) {
                return InternalValidationResult.reject(
                    "Block with root %s hasn't been imported", envelope.getBeaconBlockRoot());
              }
              final BeaconState state = maybeState.get();
              final ExecutionPayloadHeaderEip7732 header =
                  ExecutionPayloadHeaderEip7732.required(
                      BeaconStateEip7732.required(state).getLatestExecutionPayloadHeader());
              /*
               * [REJECT] envelope.builder_index == header.builder_index
               */
              if (!envelope.getBuilderIndex().equals(header.getBuilderIndex())) {
                return InternalValidationResult.reject(
                    "Envelope builder index does not match the committed builder index");
              }

              /*
               * if envelope.payload_withheld == False then
               * [REJECT] payload.block_hash == header.block_hash
               */
              if (envelope.isPayloadWithheld()
                  && !envelope.getPayload().getBlockHash().equals(header.getBlockHash())) {
                return InternalValidationResult.reject(
                    "Payload block hash does not match the committed block hash");
              }
              /*
               * [REJECT] The builder signature, signed_execution_payload_envelope.signature, is valid with respect to the builder's public key.
               */
              final Validator builder =
                  state.getValidators().get(envelope.getBuilderIndex().intValue());
              if (!verifyBuilderSignature(
                  builder.getPublicKey(), signedExecutionPayloadEnvelope, state)) {
                return InternalValidationResult.reject(
                    "The builder signature is not valid for a builder with public key %s",
                    builder.getPublicKey());
              }

              // cache the valid envelope
              receivedValidEnvelopeInfoSet.add(blockRootAndBuilderIndex);

              return InternalValidationResult.ACCEPT;
            });
  }

  private boolean verifyBuilderSignature(
      final BLSPublicKey publicKey,
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope,
      final BeaconState state) {
    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_BUILDER,
            spec.getCurrentEpoch(state),
            state.getFork(),
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot =
        spec.computeSigningRoot(signedExecutionPayloadEnvelope.getMessage(), domain);
    return BLS.verify(publicKey, signingRoot, signedExecutionPayloadEnvelope.getSignature());
  }

  record BlockRootAndBuilderIndex(Bytes32 blockRoot, UInt64 builderIndex) {}
}
