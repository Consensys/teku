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

package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.getAggregatorModulo;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.isAggregator;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_SELECTION_PROOF;
import static tech.pegasys.teku.util.config.Constants.VALID_AGGREGATE_SET_SIZE;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.collections.ConcurrentLimitedSet;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.config.Constants;

public class SignedAggregateAndProofValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Set<AggregatorIndexAndEpoch> receivedAggregatorIndexAndEpochs =
      ConcurrentLimitedSet.create(
          VALID_AGGREGATE_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
  private final Set<Bytes32> receivedValidAggregations =
      ConcurrentLimitedSet.create(
          VALID_AGGREGATE_SET_SIZE, LimitStrategy.DROP_LEAST_RECENTLY_ACCESSED);
  private final AttestationValidator attestationValidator;
  private final RecentChainData recentChainData;

  public SignedAggregateAndProofValidator(
      final AttestationValidator attestationValidator, final RecentChainData recentChainData) {
    this.attestationValidator = attestationValidator;
    this.recentChainData = recentChainData;
  }

  public void addSeenAggregate(final ValidateableAttestation attestation) {
    receivedValidAggregations.add(attestation.hash_tree_root());
  }

  public InternalValidationResult validate(final ValidateableAttestation attestation) {
    final SignedAggregateAndProof signedAggregate = attestation.getSignedAggregateAndProof();
    final AggregateAndProof aggregateAndProof = signedAggregate.getMessage();
    final Attestation aggregate = aggregateAndProof.getAggregate();

    final UnsignedLong aggregateSlot = aggregate.getData().getSlot();
    final AggregatorIndexAndEpoch aggregatorIndexAndEpoch =
        new AggregatorIndexAndEpoch(
            aggregateAndProof.getIndex(), compute_epoch_at_slot(aggregateSlot));
    if (receivedAggregatorIndexAndEpochs.contains(aggregatorIndexAndEpoch)) {
      LOG.trace("Ignoring duplicate aggregate");
      return IGNORE;
    }

    if (receivedValidAggregations.contains(attestation.hash_tree_root())) {
      LOG.trace("Ignoring duplicate aggregate based on hash tree root");
      return IGNORE;
    }

    final InternalValidationResult aggregateInternalValidationResult =
        attestationValidator.singleOrAggregateAttestationChecks(aggregate);
    if (aggregateInternalValidationResult == REJECT
        || aggregateInternalValidationResult == IGNORE) {
      LOG.trace("Rejecting aggregate because attestation failed validation");
      return aggregateInternalValidationResult;
    }

    final Optional<BeaconState> maybeState =
        recentChainData.getBlockState(aggregate.getData().getBeacon_block_root());
    if (maybeState.isEmpty()) {
      return SAVE_FOR_FUTURE;
    }
    final BeaconState state = maybeState.get();
    final BLSPublicKey aggregatorPublicKey =
        ValidatorsUtil.getValidatorPubKey(state, aggregateAndProof.getIndex());

    if (!isSelectionProofValid(
        aggregateSlot, state, aggregatorPublicKey, aggregateAndProof.getSelection_proof())) {
      LOG.trace("Rejecting aggregate with incorrect selection proof");
      return REJECT;
    }

    final List<Integer> beaconCommittee =
        CommitteeUtil.get_beacon_committee(state, aggregateSlot, aggregate.getData().getIndex());

    final int aggregatorModulo = getAggregatorModulo(beaconCommittee.size());
    if (!isAggregator(aggregateAndProof.getSelection_proof(), aggregatorModulo)) {
      LOG.trace(
          "Rejecting aggregate because selection proof does not select validator as aggregator");
      return REJECT;
    }
    if (!beaconCommittee.contains(toIntExact(aggregateAndProof.getIndex().longValue()))) {
      LOG.trace(
          "Rejecting aggregate because attester is not in committee. Should have been one of {}",
          beaconCommittee);
      return REJECT;
    }

    if (!isSignatureValid(signedAggregate, state, aggregatorPublicKey)) {
      LOG.trace("Rejecting aggregate with invalid signature");
      return REJECT;
    }

    if (!receivedAggregatorIndexAndEpochs.add(aggregatorIndexAndEpoch)) {
      LOG.trace("Ignoring duplicate aggregate");
      return IGNORE;
    }

    if (!receivedValidAggregations.add(attestation.hash_tree_root())) {
      LOG.trace("Ignoring duplicate aggregate based on hash tree root");
      return IGNORE;
    }

    return aggregateInternalValidationResult;
  }

  private boolean isSignatureValid(
      final SignedAggregateAndProof signedAggregate,
      final BeaconState state,
      final BLSPublicKey aggregatorPublicKey) {
    final AggregateAndProof aggregateAndProof = signedAggregate.getMessage();
    final Bytes domain =
        get_domain(
            Constants.DOMAIN_AGGREGATE_AND_PROOF,
            compute_epoch_at_slot(aggregateAndProof.getAggregate().getData().getSlot()),
            state.getFork(),
            state.getGenesis_validators_root());
    final Bytes signingRoot = compute_signing_root(aggregateAndProof, domain);
    return BLS.verify(aggregatorPublicKey, signingRoot, signedAggregate.getSignature());
  }

  private boolean isSelectionProofValid(
      final UnsignedLong aggregateSlot,
      final BeaconState state,
      final BLSPublicKey aggregatorPublicKey,
      final BLSSignature selectionProof) {
    final Bytes domain =
        get_domain(
            DOMAIN_SELECTION_PROOF,
            compute_epoch_at_slot(aggregateSlot),
            state.getFork(),
            state.getGenesis_validators_root());
    final Bytes signingRoot = compute_signing_root(aggregateSlot.longValue(), domain);
    return BLS.verify(aggregatorPublicKey, signingRoot, selectionProof);
  }

  private static class AggregatorIndexAndEpoch {
    private final UnsignedLong aggregatorIndex;
    private final UnsignedLong epoch;

    private AggregatorIndexAndEpoch(final UnsignedLong aggregatorIndex, final UnsignedLong epoch) {
      this.aggregatorIndex = aggregatorIndex;
      this.epoch = epoch;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AggregatorIndexAndEpoch that = (AggregatorIndexAndEpoch) o;
      return Objects.equals(aggregatorIndex, that.aggregatorIndex)
          && Objects.equals(epoch, that.epoch);
    }

    @Override
    public int hashCode() {
      return Objects.hash(aggregatorIndex, epoch);
    }
  }
}
