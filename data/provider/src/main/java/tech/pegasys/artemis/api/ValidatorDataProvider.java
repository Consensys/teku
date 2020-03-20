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

import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.artemis.api.exceptions.ChainDataUnavailableException;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconBlock;
import tech.pegasys.artemis.api.schema.ValidatorDuties;
import tech.pegasys.artemis.api.schema.ValidatorDutiesRequest;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class ValidatorDataProvider {
  private volatile ValidatorCoordinator validatorCoordinator;
  private CombinedChainDataClient combinedChainDataClient;
  private static final Logger LOG = LogManager.getLogger();

  public ValidatorDataProvider(
      ValidatorCoordinator validatorCoordinator, CombinedChainDataClient combinedChainDataClient) {
    this.validatorCoordinator = validatorCoordinator;
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
    final Optional<Bytes32> optionalBlockRoot = combinedChainDataClient.getBestBlockRoot();
    if (optionalBlockRoot.isEmpty()) {
      return completedFuture(List.of());
    }

    UnsignedLong epoch = validatorDutiesRequest.epoch;
    UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);
    final Bytes32 headBlockRoot = optionalBlockRoot.get();
    return combinedChainDataClient
        .getStateAtSlot(slot, headBlockRoot)
        .thenApply(
            state -> getValidatorDutiesFromState(state.get(), validatorDutiesRequest.pubkeys))
        .exceptionally(err -> List.of());
  }

  @VisibleForTesting
  protected List<ValidatorDuties> getValidatorDutiesFromState(
      final tech.pegasys.artemis.datastructures.state.BeaconState state,
      final List<BLSPubKey> pubKeys) {
    final List<ValidatorDuties> dutiesList = new ArrayList<>();

    final List<CommitteeAssignment> committees =
        combinedChainDataClient.getCommitteesFromState(state, state.getSlot());
    final UnsignedLong firstSlot = state.getSlot();
    Map<BLSPubKey, List<UnsignedLong>> proposers = new HashMap<>();

    for (int i = 0; i < Constants.SLOTS_PER_EPOCH; i++) {
      final UnsignedLong thisSlot = firstSlot.plus(UnsignedLong.valueOf(i));
      BLSPublicKey publicKey = validatorCoordinator.getProposerForSlot(state, thisSlot);
      BLSPubKey pubkey = new BLSPubKey(publicKey.toBytes());
      if (proposers.containsKey(pubkey)) {
        List<UnsignedLong> proposalSlots = proposers.get(pubkey);
        proposalSlots.add(thisSlot);
      } else {
        proposers.put(pubkey, List.of(thisSlot).stream().collect(Collectors.toList()));
      }
    }

    for (final BLSPubKey pubKey : pubKeys) {
      final Integer validatorIndex = getValidatorIndex(state.getValidators().asList(), pubKey);
      if (validatorIndex == null) {
        dutiesList.add(new ValidatorDuties(pubKey, null, null, List.of()));
      } else {
        List<UnsignedLong> proposedSlots = proposers.getOrDefault(pubKey, List.of());
        dutiesList.add(
            new ValidatorDuties(
                pubKey,
                validatorIndex,
                getCommitteeIndex(committees, validatorIndex),
                proposedSlots));
      }
    }
    return dutiesList;
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
}
