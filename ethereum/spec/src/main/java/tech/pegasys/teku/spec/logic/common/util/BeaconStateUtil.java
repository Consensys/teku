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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkData;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.ssz.Merkleizable;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

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

  public Bytes32 computeDomain(Bytes4 domainType) {
    return computeDomain(domainType, specConfig.getGenesisForkVersion(), Bytes32.ZERO);
  }

  public Bytes32 getPreviousDutyDependentRoot(BeaconState state) {
    return getDutyDependentRoot(state, beaconStateAccessors.getPreviousEpoch(state));
  }

  public Bytes32 getCurrentDutyDependentRoot(BeaconState state) {
    return getDutyDependentRoot(state, beaconStateAccessors.getCurrentEpoch(state));
  }

  public Bytes4 computeForkDigest(Bytes4 currentVersion, Bytes32 genesisValidatorsRoot) {
    return new Bytes4(computeForkDataRoot(currentVersion, genesisValidatorsRoot).slice(0, 4));
  }

  public Bytes32 getDomain(BeaconState state, Bytes4 domainType, UInt64 messageEpoch) {
    UInt64 epoch =
        (messageEpoch == null) ? beaconStateAccessors.getCurrentEpoch(state) : messageEpoch;
    return getDomain(domainType, epoch, state.getFork(), state.getGenesis_validators_root());
  }

  public Bytes32 getDomain(BeaconState state, Bytes4 domainType) {
    return getDomain(state, domainType, null);
  }

  public Bytes32 getDomain(
      final Bytes4 domainType,
      final UInt64 epoch,
      final Fork fork,
      final Bytes32 genesisValidatorsRoot) {
    Bytes4 forkVersion =
        (epoch.compareTo(fork.getEpoch()) < 0)
            ? fork.getPrevious_version()
            : fork.getCurrent_version();
    return computeDomain(domainType, forkVersion, genesisValidatorsRoot);
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

  public Bytes computeSigningRoot(Merkleizable object, Bytes32 domain) {
    return new SigningData(object.hashTreeRoot(), domain).hashTreeRoot();
  }

  public Bytes computeSigningRoot(UInt64 number, Bytes32 domain) {
    SigningData domainWrappedObject = new SigningData(SszUInt64.of(number).hashTreeRoot(), domain);
    return domainWrappedObject.hashTreeRoot();
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
    return beaconStateAccessors.getBeaconCommittee(state, slot, committeeIndex).stream()
        .map(validatorIndex -> state.getValidators().get(validatorIndex).getEffective_balance());
  }

  public Bytes32 computeSigningRoot(Bytes bytes, Bytes32 domain) {
    SigningData domainWrappedObject =
        new SigningData(SszByteVector.computeHashTreeRoot(bytes), domain);
    return domainWrappedObject.hashTreeRoot();
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

  private Bytes32 computeDomain(
      Bytes4 domainType, Bytes4 forkVersion, Bytes32 genesisValidatorsRoot) {
    final Bytes32 forkDataRoot = computeForkDataRoot(forkVersion, genesisValidatorsRoot);
    return computeDomain(domainType, forkDataRoot);
  }

  private Bytes32 computeDomain(final Bytes4 domainType, final Bytes32 forkDataRoot) {
    return Bytes32.wrap(Bytes.concatenate(domainType.getWrappedBytes(), forkDataRoot.slice(0, 28)));
  }

  private Bytes32 computeForkDataRoot(Bytes4 currentVersion, Bytes32 genesisValidatorsRoot) {
    return new ForkData(currentVersion, genesisValidatorsRoot).hashTreeRoot();
  }

  private Bytes32 getDutyDependentRoot(final BeaconState state, final UInt64 epoch) {
    final UInt64 slot = miscHelpers.computeStartSlotAtEpoch(epoch).minusMinZero(1);
    return slot.equals(state.getSlot())
        // No previous block, use algorithm for calculating the genesis block root
        ? BeaconBlock.fromGenesisState(schemaDefinitions, state).getRoot()
        : beaconStateAccessors.getBlockRootAtSlot(state, slot);
  }
}
