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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.api.exceptions.ChainDataUnavailableException;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.api.schema.AttestationData;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconChainHead;
import tech.pegasys.artemis.api.schema.BeaconHead;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.api.schema.BeaconValidators;
import tech.pegasys.artemis.api.schema.Committee;
import tech.pegasys.artemis.api.schema.Fork;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.api.schema.ValidatorDuties;
import tech.pegasys.artemis.api.schema.ValidatorDutiesRequest;
import tech.pegasys.artemis.api.schema.ValidatorsRequest;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public class ChainDataProvider {
  private final CombinedChainDataClient combinedChainDataClient;

  private final ChainStorageClient chainStorageClient;

  public ChainDataProvider(
      final ChainStorageClient chainStorageClient,
      final CombinedChainDataClient combinedChainDataClient) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.chainStorageClient = chainStorageClient;
  }

  public UnsignedLong getGenesisTime() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    return chainStorageClient.getGenesisTime();
  }

  public BeaconHead getBeaconHead() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    Bytes32 headBlockRoot =
        chainStorageClient.getBestBlockRoot().orElseThrow(ChainDataUnavailableException::new);
    tech.pegasys.artemis.datastructures.blocks.BeaconBlock headBlock =
        chainStorageClient
            .getBlockByRoot(headBlockRoot)
            .orElseThrow(ChainDataUnavailableException::new);

    return new BeaconHead(headBlock.getSlot(), headBlockRoot, headBlock.getState_root());
  }

  public Fork getFork() {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }

    tech.pegasys.artemis.datastructures.state.BeaconState bestBlockRootState =
        chainStorageClient.getBestBlockRootState().orElseThrow(ChainDataUnavailableException::new);
    return new Fork(bestBlockRootState.getFork());
  }

  public SafeFuture<List<Committee>> getCommitteesAtEpoch(UnsignedLong epoch) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getCommitteeAssignmentAtEpoch(epoch)
        .thenApply(result -> result.stream().map(Committee::new).collect(Collectors.toList()))
        .exceptionally(err -> List.of());
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockBySlot(UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getBlockBySlot(slot)
        .thenApply(block -> block.map(SignedBeaconBlock::new));
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient != null && combinedChainDataClient.isStoreAvailable();
  }

  public Optional<Bytes32> getBestBlockRoot() {
    return combinedChainDataClient.getBestBlockRoot();
  }

  ChainStorageClient getChainStorageClient() {
    return chainStorageClient;
  }

  CombinedChainDataClient getCombinedChainDataClient() {
    return combinedChainDataClient;
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(Bytes32 blockParam) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getBlockByBlockRoot(blockParam)
        .thenApply(block -> block.map(SignedBeaconBlock::new));
  }

  public SafeFuture<Optional<BeaconState>> getStateByBlockRoot(Bytes32 blockRoot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .thenApply(state -> state.map(BeaconState::new))
        .exceptionally(err -> Optional.empty());
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlot(UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getStateAtSlot(slot)
        .thenApply(state -> state.map(BeaconState::new))
        .exceptionally(err -> Optional.empty());
  }

  public SafeFuture<Optional<Bytes32>> getHashTreeRootAtSlot(UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return combinedChainDataClient
        .getStateAtSlot(slot)
        .thenApply(state -> Optional.of(state.get().hash_tree_root()))
        .exceptionally(err -> Optional.empty());
  }

  public Optional<Attestation> getUnsignedAttestationAtSlot(
      UnsignedLong slot, Integer committeeIndex) {
    if (!isStoreAvailable()) {
      throw new ChainDataUnavailableException();
    }
    if (isFinalized(slot)) {
      throw new IllegalArgumentException(
          String.format("Slot %s is finalized, no attestation will be created.", slot.toString()));
    }
    Optional<tech.pegasys.artemis.datastructures.blocks.BeaconBlock> block =
        chainStorageClient.getBlockBySlot(slot);
    Optional<tech.pegasys.artemis.datastructures.state.BeaconState> state =
        chainStorageClient.getBestBlockRootState();
    if (block.isEmpty() || state.isEmpty()) {
      return Optional.empty();
    }

    int committeeCount = get_committee_count_at_slot(state.get(), slot).intValue();
    if (committeeIndex < 0 || committeeIndex >= committeeCount) {
      throw new IllegalArgumentException(
          "Invalid committee index provided - expected between 0 and " + (committeeCount - 1));
    }

    tech.pegasys.artemis.datastructures.operations.AttestationData internalAttestation =
        AttestationUtil.getGenericAttestationData(state.get(), block.get());
    AttestationData data = new AttestationData(internalAttestation);
    Bitlist aggregationBits = AttestationUtil.getAggregationBits(committeeCount, committeeIndex);
    Attestation attestation = new Attestation(aggregationBits, data, BLSSignature.empty());
    return Optional.of(attestation);
  }

  public boolean isFinalized(SignedBeaconBlock signedBeaconBlock) {
    return combinedChainDataClient.isFinalized(signedBeaconBlock.message.slot);
  }

  public boolean isFinalized(UnsignedLong slot) {
    return combinedChainDataClient.isFinalized(slot);
  }

  public SafeFuture<Optional<BeaconValidators>> getValidatorsByValidatorsRequest(
      final ValidatorsRequest request) {
    UnsignedLong slot =
        request.epoch == null
            ? chainStorageClient.getBestSlot()
            : BeaconStateUtil.compute_start_slot_at_epoch(request.epoch);

    return getStateAtSlot(slot)
        .thenApply(
            optionalBeaconState -> {
              if (optionalBeaconState.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(new BeaconValidators(optionalBeaconState.get(), request.pubkeys));
            });
  }

  public SafeFuture<List<ValidatorDuties>> getValidatorDutiesByRequest(
      final ValidatorDutiesRequest validatorDutiesRequest) {

    if (validatorDutiesRequest == null || !isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    final Optional<Bytes32> optionalBlockRoot = getBestBlockRoot();
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

    for (final BLSPubKey pubKey : pubKeys) {
      final ValidatorDuties validatorDuties =
          ValidatorsUtil.getValidatorIndex(state, BLSPublicKey.fromBytes(pubKey.toBytes()))
              .map(
                  validatorIndex ->
                      new ValidatorDuties(
                          pubKey, validatorIndex, getCommitteeIndex(committees, validatorIndex)))
              .orElseGet(() -> new ValidatorDuties(pubKey, null, null));
      dutiesList.add(validatorDuties);
    }
    return dutiesList;
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

  public Optional<BeaconChainHead> getHeadState() {
    return combinedChainDataClient.getHeadStateFromStore().map(BeaconChainHead::new);
  }
}
