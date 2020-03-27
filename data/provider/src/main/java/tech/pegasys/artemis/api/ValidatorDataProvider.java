/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.api;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.api.schema.AttestationData;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconBlock;
import tech.pegasys.artemis.api.schema.ValidatorDuties;
import tech.pegasys.artemis.api.schema.ValidatorDutiesRequest;
import tech.pegasys.artemis.storage.ChainDataUnavailableException;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorDuties.Duties;

public class ValidatorDataProvider {
  private final ValidatorApiChannel validatorApiChannel;
  private CombinedChainDataClient combinedChainDataClient;

  public ValidatorDataProvider(
      final ValidatorApiChannel validatorApiChannel,
      final CombinedChainDataClient combinedChainDataClient) {
    this.validatorApiChannel = validatorApiChannel;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient.isStoreAvailable();
  }

  public boolean isEpochFinalized(final UnsignedLong epoch) {
    return combinedChainDataClient.isFinalizedEpoch(epoch);
  }

  public SafeFuture<Optional<BeaconBlock>> getUnsignedBeaconBlockAtSlot(
      UnsignedLong slot, BLSSignature randao) {
    if (slot == null) {
      throw new IllegalArgumentException("no slot provided.");
    }
    if (randao == null) {
      throw new IllegalArgumentException("no randao_reveal provided.");
    }

    return validatorApiChannel
        .createUnsignedBlock(
            slot, tech.pegasys.artemis.util.bls.BLSSignature.fromBytes(randao.getBytes()))
        .thenApply(maybeBlock -> maybeBlock.map(BeaconBlock::new));
  }

  public SafeFuture<Optional<Attestation>> createUnsignedAttestationAtSlot(
      UnsignedLong slot, int committeeIndex) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return validatorApiChannel
        .createUnsignedAttestation(slot, committeeIndex)
        .thenApply(
            maybeAttestation ->
                maybeAttestation.map(
                    attestation ->
                        new Attestation(
                            attestation.getAggregation_bits(),
                            new AttestationData(attestation.getData()),
                            new BLSSignature(attestation.getAggregate_signature()))));
  }

  public SafeFuture<Optional<List<ValidatorDuties>>> getValidatorDutiesByRequest(
      final ValidatorDutiesRequest validatorDutiesRequest) {
    checkArgument(validatorDutiesRequest != null, "Must supply a valid request");
    if (validatorDutiesRequest.pubkeys.isEmpty()) {
      // Short-cut if there's nothing to look up
      return SafeFuture.completedFuture(Optional.of(Collections.emptyList()));
    }
    if (!combinedChainDataClient.isStoreAvailable()
        || combinedChainDataClient.getBestBlockRoot().isEmpty()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return SafeFuture.of(
            () -> {
              final List<BLSPublicKey> publicKeys =
                  validatorDutiesRequest.pubkeys.stream()
                      .map(key -> BLSPublicKey.fromBytes(key.toBytes()))
                      .collect(toList());
              return validatorApiChannel.getDuties(validatorDutiesRequest.epoch, publicKeys);
            })
        .thenApply(
            res ->
                res.map(duties -> duties.stream().map(this::mapToSchemaDuties).collect(toList())));
  }

  private ValidatorDuties mapToSchemaDuties(
      final tech.pegasys.artemis.validator.api.ValidatorDuties duty) {
    final BLSPubKey pubKey = new BLSPubKey(duty.getPublicKey().toBytesCompressed());
    if (duty.getDuties().isEmpty()) {
      return new ValidatorDuties(pubKey, null, null, emptyList(), null);
    }
    final Duties duties = duty.getDuties().get();
    return new ValidatorDuties(
        pubKey,
        duties.getValidatorIndex(),
        duties.getAttestationCommitteeIndex(),
        duties.getBlockProposalSlots(),
        duties.getAttestationSlot());
  }

  public void submitAttestation(Attestation attestation) {
    // TODO extra validation for the attestation we're posting?
    if (attestation.signature.asInternalBLSSignature().toBytes().isZero()) {
      throw new IllegalArgumentException("Signed attestations must have a non zero signature");
    }
    validatorApiChannel.sendSignedAttestation(attestation.asInternalAttestation());
  }
}
