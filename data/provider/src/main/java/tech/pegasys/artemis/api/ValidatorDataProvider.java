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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.artemis.api.exceptions.ChainDataUnavailableException;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconBlock;
import tech.pegasys.artemis.api.schema.ValidatorDuties;
import tech.pegasys.artemis.api.schema.ValidatorDutiesRequest;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorDuties.Duties;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class ValidatorDataProvider {
  private final ValidatorCoordinator validatorCoordinator;
  private final ValidatorApiChannel validatorApiChannel;
  private CombinedChainDataClient combinedChainDataClient;
  private static final Logger LOG = LogManager.getLogger();

  public ValidatorDataProvider(
      final ValidatorCoordinator validatorCoordinator,
      final ValidatorApiChannel validatorApiChannel,
      final CombinedChainDataClient combinedChainDataClient) {
    this.validatorCoordinator = validatorCoordinator;
    this.validatorApiChannel = validatorApiChannel;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient.isStoreAvailable();
  }

  public Optional<BeaconBlock> getUnsignedBeaconBlockAtSlot(UnsignedLong slot, BLSSignature randao)
      throws DataProviderException {
    if (slot == null) {
      throw new IllegalArgumentException("no slot provided.");
    }
    if (randao == null) {
      throw new IllegalArgumentException("no randao_reveal provided.");
    }

    try {
      Optional<tech.pegasys.artemis.datastructures.blocks.BeaconBlock> newBlock =
          validatorCoordinator.createUnsignedBlock(
              slot, tech.pegasys.artemis.util.bls.BLSSignature.fromBytes(randao.getBytes()));
      if (newBlock.isPresent()) {
        return Optional.of(new BeaconBlock(newBlock.get()));
      }
    } catch (Exception ex) {
      LOG.error("Failed to generate a new unsigned block", ex);
      throw new DataProviderException(ex.getMessage());
    }
    return Optional.empty();
  }

  public SafeFuture<List<ValidatorDuties>> getValidatorDutiesByRequest(
      final ValidatorDutiesRequest validatorDutiesRequest) {
    if (validatorDutiesRequest == null || !combinedChainDataClient.isStoreAvailable()) {
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
        .thenApply(duties -> duties.stream().map(this::mapToSchemaDuties).collect(toList()));
  }

  private ValidatorDuties mapToSchemaDuties(
      final tech.pegasys.artemis.validator.api.ValidatorDuties duty) {
    final BLSPubKey pubKey = new BLSPubKey(duty.getPublicKey().toBytesCompressed());
    if (duty.getDuties().isEmpty()) {
      return new ValidatorDuties(pubKey, null, null, emptyList());
    }
    final Duties duties = duty.getDuties().get();
    return new ValidatorDuties(
        pubKey,
        duties.getValidatorIndex(),
        duties.getAttestationCommitteeIndex(),
        duties.getBlockProposalSlots());
  }

  @VisibleForTesting
  protected static Integer getValidatorIndex(
      final List<tech.pegasys.artemis.datastructures.state.Validator> validators,
      final BLSPubKey publicKey) {
    Optional<tech.pegasys.artemis.datastructures.state.Validator> optionalValidator =
        validators.stream()
            .filter(
                v -> Bytes48.fromHexString(publicKey.toHexString()).equals(v.getPubkey().toBytes()))
            .findFirst();
    if (optionalValidator.isPresent()) {
      return validators.indexOf(optionalValidator.get());
    } else {
      return null;
    }
  }

  @VisibleForTesting
  protected Integer getCommitteeIndex(List<CommitteeAssignment> committees, int validatorIndex) {
    Optional<CommitteeAssignment> matchingCommittee =
        committees.stream()
            .filter(committee -> committee.getCommittee().contains(validatorIndex))
            .findFirst();
    if (matchingCommittee.isPresent()) {
      return committees.indexOf(matchingCommittee.get());
    } else {
      return null;
    }
  }

  public void submitAttestation(Attestation attestation) {
    validatorCoordinator.postSignedAttestation(attestation.asInternalAttestation(), true);
  }
}
