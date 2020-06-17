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

package tech.pegasys.teku.api;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorBlockResult;
import tech.pegasys.teku.api.schema.ValidatorDuties;
import tech.pegasys.teku.api.schema.ValidatorDutiesRequest;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorDuties.Duties;

public class ValidatorDataProvider {
  public static final String CANNOT_PRODUCE_FAR_FUTURE_BLOCK =
      "Cannot produce a block more than " + SLOTS_PER_EPOCH + " slots in the future.";
  public static final String CANNOT_PRODUCE_HISTORIC_BLOCK =
      "Cannot produce a block for a historic slot.";
  public static final String NO_SLOT_PROVIDED = "No slot was provided.";
  public static final String NO_RANDAO_PROVIDED = "No randao_reveal was provided.";
  private final ValidatorApiChannel validatorApiChannel;
  private CombinedChainDataClient combinedChainDataClient;
  private final BlockImporter blockImporter;

  public ValidatorDataProvider(
      final ValidatorApiChannel validatorApiChannel,
      final BlockImporter blockImporter,
      final CombinedChainDataClient combinedChainDataClient) {
    this.validatorApiChannel = validatorApiChannel;
    this.combinedChainDataClient = combinedChainDataClient;
    this.blockImporter = blockImporter;
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient.isStoreAvailable();
  }

  public boolean isEpochFinalized(final UnsignedLong epoch) {
    return combinedChainDataClient.isFinalizedEpoch(epoch);
  }

  public SafeFuture<Optional<BeaconBlock>> getUnsignedBeaconBlockAtSlot(
      UnsignedLong slot, BLSSignature randao, Optional<Bytes32> graffiti) {
    if (slot == null) {
      throw new IllegalArgumentException(NO_SLOT_PROVIDED);
    }
    if (randao == null) {
      throw new IllegalArgumentException(NO_RANDAO_PROVIDED);
    }
    UnsignedLong bestSlot = combinedChainDataClient.getBestSlot();
    if (bestSlot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).compareTo(slot) < 0) {
      throw new IllegalArgumentException(CANNOT_PRODUCE_FAR_FUTURE_BLOCK);
    }
    if (bestSlot.compareTo(slot) > 0) {
      throw new IllegalArgumentException(CANNOT_PRODUCE_HISTORIC_BLOCK);
    }

    return validatorApiChannel
        .createUnsignedBlock(
            slot, tech.pegasys.teku.bls.BLSSignature.fromBytes(randao.getBytes()), graffiti)
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
      final tech.pegasys.teku.validator.api.ValidatorDuties duty) {
    final BLSPubKey pubKey = new BLSPubKey(duty.getPublicKey().toBytesCompressed());
    if (duty.getDuties().isEmpty()) {
      return new ValidatorDuties(pubKey, null, null, null, null, emptyList(), null);
    }
    final Duties duties = duty.getDuties().get();
    return new ValidatorDuties(
        pubKey,
        duties.getValidatorIndex(),
        duties.getAttestationCommitteeIndex(),
        duties.getAttestationCommitteePosition(),
        duties.getAggregatorModulo(),
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

  public SafeFuture<ValidatorBlockResult> submitSignedBlock(
      final SignedBeaconBlock signedBeaconBlock) {
    return blockImporter
        .importBlockAsync(signedBeaconBlock.asInternalSignedBeaconBlock())
        .thenApply(
            blockImportResult -> {
              int responseCode;
              Bytes32 hashRoot = null;

              if (!blockImportResult.isSuccessful()) {
                if (blockImportResult.getFailureReason()
                    == BlockImportResult.FailureReason.INTERNAL_ERROR) {
                  responseCode = 500;
                } else {
                  responseCode = 202;
                }
              } else {
                responseCode = 200;
                hashRoot = blockImportResult.getBlock().getMessage().hash_tree_root();
              }

              return new ValidatorBlockResult(
                  responseCode, blockImportResult.getFailureCause(), Optional.ofNullable(hashRoot));
            });
  }
}
