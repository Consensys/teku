/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.util;

import static java.util.stream.Collectors.toUnmodifiableList;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

@SuppressWarnings("unused")
public class BeaconStateUtil {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfig specConfig;
  private final SchemaDefinitions schemaDefinitions;

  private final Predicates predicates;
  private final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  public BeaconStateUtil(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.predicates = predicates;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  public boolean isValidGenesisState(UInt64 genesisTime, int activeValidatorCount) {
    return isItMinGenesisTimeYet(genesisTime)
        && isThereEnoughNumberOfValidators(activeValidatorCount);
  }

  private boolean isThereEnoughNumberOfValidators(int activeValidatorCount) {
    return activeValidatorCount >= specConfig.getMinGenesisActiveValidatorCount();
  }

  private boolean isItMinGenesisTimeYet(final UInt64 genesisTime) {
    return genesisTime.compareTo(specConfig.getMinGenesisTime()) >= 0;
  }

  public UInt64 computeNextEpochBoundary(final UInt64 slot) {
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(slot);
    return miscHelpers.computeStartSlotAtEpoch(currentEpoch).equals(slot)
        ? currentEpoch
        : currentEpoch.plus(1);
  }

  public Bytes32 getPreviousDutyDependentRoot(BeaconState state) {
    return getDutyDependentRoot(state, beaconStateAccessors.getPreviousEpoch(state));
  }

  public Bytes32 getCurrentDutyDependentRoot(BeaconState state) {
    return getDutyDependentRoot(state, beaconStateAccessors.getCurrentEpoch(state));
  }

  public List<UInt64> getEffectiveBalances(final BeaconState state) {
    return BeaconStateCache.getTransitionCaches(state)
        .getEffectiveBalances()
        .get(
            beaconStateAccessors.getCurrentEpoch(state),
            epoch ->
                state.getValidators().stream()
                    .map(
                        validator ->
                            predicates.isActiveValidator(validator, epoch)
                                ? validator.getEffective_balance()
                                : UInt64.ZERO)
                    .collect(toUnmodifiableList()));
  }

  public boolean all(SszBitvector bitvector, int start, int end) {
    for (int i = start; i < end; i++) {
      if (!bitvector.getBit(i)) {
        return false;
      }
    }
    return true;
  }

  public UInt64 getAttestersTotalEffectiveBalance(final BeaconState state, final UInt64 slot) {
    beaconStateAccessors.validateStateForCommitteeQuery(state, slot);
    return BeaconStateCache.getTransitionCaches(state)
        .getAttestersTotalBalance()
        .get(
            slot,
            p -> {
              final SszList<Validator> validators = state.getValidators();
              final UInt64 committeeCount =
                  beaconStateAccessors.getCommitteeCountPerSlot(
                      state, miscHelpers.computeEpochAtSlot(slot));
              return UInt64.range(UInt64.ZERO, committeeCount)
                  .flatMap(committee -> streamEffectiveBalancesForCommittee(state, slot, committee))
                  .reduce(UInt64.ZERO, UInt64::plus);
            });
  }

  private Stream<UInt64> streamEffectiveBalancesForCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 committeeIndex) {
    return beaconStateAccessors
        .getBeaconCommittee(state, slot, committeeIndex)
        .intStream()
        .mapToObj(
            validatorIndex -> state.getValidators().get(validatorIndex).getEffective_balance());
  }

  public int computeSubnetForAttestation(final BeaconState state, final Attestation attestation) {
    final UInt64 attestationSlot = attestation.getData().getSlot();
    final UInt64 committeeIndex = attestation.getData().getIndex();
    return computeSubnetForCommittee(state, attestationSlot, committeeIndex);
  }

  public int computeSubnetForCommittee(
      final UInt64 attestationSlot, final UInt64 committeeIndex, final UInt64 committeesPerSlot) {
    final UInt64 slotsSinceEpochStart = attestationSlot.mod(specConfig.getSlotsPerEpoch());
    final UInt64 committeesSinceEpochStart = committeesPerSlot.times(slotsSinceEpochStart);
    return committeesSinceEpochStart.plus(committeeIndex).mod(ATTESTATION_SUBNET_COUNT).intValue();
  }

  private int computeSubnetForCommittee(
      final BeaconState state, final UInt64 attestationSlot, final UInt64 committeeIndex) {
    return computeSubnetForCommittee(
        attestationSlot,
        committeeIndex,
        beaconStateAccessors.getCommitteeCountPerSlot(
            state, miscHelpers.computeEpochAtSlot(attestationSlot)));
  }

  private Bytes32 getDutyDependentRoot(final BeaconState state, final UInt64 epoch) {
    final UInt64 slot = miscHelpers.computeStartSlotAtEpoch(epoch).minusMinZero(1);
    return slot.equals(state.getSlot())
        // No previous block, use algorithm for calculating the genesis block root
        ? BeaconBlock.fromGenesisState(schemaDefinitions, state).getRoot()
        : beaconStateAccessors.getBlockRootAtSlot(state, slot);
  }
}
