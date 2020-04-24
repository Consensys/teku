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

package tech.pegasys.artemis.networking.eth2.gossip.topics.validation;

import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.CommitteeUtil.getAggregatorModulo;
import static tech.pegasys.artemis.datastructures.util.CommitteeUtil.isAggregator;
import static tech.pegasys.artemis.networking.eth2.gossip.topics.validation.ValidationResult.INVALID;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_SELECTION_PROOF;
import static tech.pegasys.artemis.util.config.Constants.VALID_BLOCK_SET_SIZE;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.BLS;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.CommitteeUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.collections.ConcurrentLimitedSet;
import tech.pegasys.artemis.util.collections.LimitStrategy;
import tech.pegasys.artemis.util.config.Constants;

public class AggregateAndProofValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Set<AggregatorIndexAndSlot> receivedValidAggregations =
      ConcurrentLimitedSet.create(VALID_BLOCK_SET_SIZE, LimitStrategy.DROP_OLDEST_ELEMENT);
  private final AttestationValidator attestationValidator;
  private final RecentChainData recentChainData;

  public AggregateAndProofValidator(
      final AttestationValidator attestationValidator, final RecentChainData recentChainData) {
    this.attestationValidator = attestationValidator;
    this.recentChainData = recentChainData;
  }

  public ValidationResult validate(final SignedAggregateAndProof signedAggregate) {
    final AggregateAndProof aggregateAndProof = signedAggregate.getMessage();
    final Attestation aggregate = aggregateAndProof.getAggregate();

    final UnsignedLong aggregateSlot = aggregate.getData().getSlot();
    final AggregatorIndexAndSlot aggregatorIndexAndSlot =
        new AggregatorIndexAndSlot(aggregateAndProof.getIndex(), aggregateSlot);
    if (receivedValidAggregations.contains(aggregatorIndexAndSlot)) {
      LOG.trace("Rejecting duplicate aggregate");
      return INVALID;
    }

    final ValidationResult aggregateValidationResult =
        attestationValidator.singleOrAggregateAttestationChecks(aggregate);
    if (aggregateValidationResult == INVALID) {
      LOG.trace("Rejecting aggregate because attestation failed validation");
      return aggregateValidationResult;
    }

    // State must be present because the aggregate is valid
    final BeaconState state =
        recentChainData.getBlockState(aggregate.getData().getBeacon_block_root()).orElseThrow();
    final BLSPublicKey aggregatorPublicKey =
        ValidatorsUtil.getValidatorPubKey(state, aggregateAndProof.getIndex());

    if (!isSelectionProofValid(
        aggregateSlot, state, aggregatorPublicKey, aggregateAndProof.getSelection_proof())) {
      LOG.trace("Rejecting aggregate with incorrect selection proof");
      return INVALID;
    }

    final List<Integer> beaconCommittee =
        CommitteeUtil.get_beacon_committee(state, aggregateSlot, aggregate.getData().getIndex());

    final int aggregatorModulo = getAggregatorModulo(beaconCommittee.size());
    if (!isAggregator(aggregateAndProof.getSelection_proof(), aggregatorModulo)) {
      LOG.trace(
          "Rejecting aggregate because selection proof does not select validator as aggregator");
      return INVALID;
    }
    if (!beaconCommittee.contains(toIntExact(aggregateAndProof.getIndex().longValue()))) {
      LOG.trace(
          "Rejecting aggregate because attester is not in committee. Should have been one of {}",
          beaconCommittee);
      return INVALID;
    }

    if (!isSignatureValid(signedAggregate, state, aggregatorPublicKey)) {
      LOG.trace("Rejecting aggregate with invalid signature");
      return INVALID;
    }

    if (!receivedValidAggregations.add(aggregatorIndexAndSlot)) {
      LOG.trace("Rejecting duplicate aggregate");
      return INVALID;
    }
    return aggregateValidationResult;
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

  private static class AggregatorIndexAndSlot {
    private final UnsignedLong aggregatorIndex;
    private final UnsignedLong slot;

    private AggregatorIndexAndSlot(final UnsignedLong aggregatorIndex, final UnsignedLong slot) {
      this.aggregatorIndex = aggregatorIndex;
      this.slot = slot;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final AggregatorIndexAndSlot that = (AggregatorIndexAndSlot) o;
      return Objects.equals(aggregatorIndex, that.aggregatorIndex)
          && Objects.equals(slot, that.slot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(aggregatorIndex, slot);
    }
  }
}
