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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;

import java.nio.ByteOrder;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateCache;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class BeaconStateUtil {
  private final SpecConstants specConstants;
  private final Spec spec;

  public BeaconStateUtil(final Spec spec) {
    this.spec = spec;
    this.specConstants = spec.getConstants();
  }

  public boolean isValidGenesisState(UInt64 genesisTime, int activeValidatorCount) {
    return isItMinGenesisTimeYet(genesisTime)
        && isThereEnoughNumberOfValidators(activeValidatorCount);
  }

  private boolean isThereEnoughNumberOfValidators(int activeValidatorCount) {
    return activeValidatorCount >= specConstants.getMinGenesisActiveValidatorCount();
  }

  private boolean isItMinGenesisTimeYet(final UInt64 genesisTime) {
    return genesisTime.compareTo(specConstants.getMinGenesisTime()) >= 0;
  }

  public UInt64 computeEpochAtSlot(UInt64 slot) {
    // TODO this should take into account hard forks
    return slot.dividedBy(specConstants.getSlotsPerEpoch());
  }

  public UInt64 getCurrentEpoch(BeaconState state) {
    return computeEpochAtSlot(state.getSlot());
  }

  UInt64 getNextEpoch(BeaconState state) {
    return getCurrentEpoch(state).plus(UInt64.ONE);
  }

  public UInt64 getPreviousEpoch(BeaconState state) {
    UInt64 currentEpoch = getCurrentEpoch(state);
    return currentEpoch.equals(UInt64.valueOf(specConstants.getGenesisEpoch()))
        ? UInt64.valueOf(specConstants.getGenesisEpoch())
        : currentEpoch.minus(UInt64.ONE);
  }

  public UInt64 computeNextEpochBoundary(final UInt64 slot) {
    final UInt64 currentEpoch = computeEpochAtSlot(slot);
    return computeStartSlotAtEpoch(currentEpoch).equals(slot) ? currentEpoch : currentEpoch.plus(1);
  }

  public Bytes32 getBlockRootAtSlot(BeaconState state, UInt64 slot)
      throws IllegalArgumentException {
    checkArgument(
        isBlockRootAvailableFromState(state, slot),
        "Block at slot %s not available from state at slot %s",
        slot,
        state.getSlot());
    int latestBlockRootIndex = slot.mod(specConstants.getSlotsPerHistoricalRoot()).intValue();
    return state.getBlock_roots().get(latestBlockRootIndex);
  }

  public Bytes32 getBlockRoot(BeaconState state, UInt64 epoch) throws IllegalArgumentException {
    return getBlockRootAtSlot(state, computeStartSlotAtEpoch(epoch));
  }

  public UInt64 computeStartSlotAtEpoch(UInt64 epoch) {
    return epoch.times(specConstants.getSlotsPerEpoch());
  }

  public Bytes32 getSeed(BeaconState state, UInt64 epoch, Bytes4 domain_type)
      throws IllegalArgumentException {
    UInt64 randaoIndex =
        epoch.plus(
            specConstants.getEpochsPerHistoricalVector() - specConstants.getMinSeedLookahead() - 1);
    Bytes32 mix = getRandaoMix(state, randaoIndex);
    Bytes epochBytes = uintToBytes(epoch.longValue(), 8);
    return Hash.sha2_256(Bytes.concatenate(domain_type.getWrappedBytes(), epochBytes, mix));
  }

  public Bytes32 getRandaoMix(BeaconState state, UInt64 epoch) {
    int index = epoch.mod(specConstants.getEpochsPerHistoricalVector()).intValue();
    return state.getRandao_mixes().get(index);
  }

  public static Bytes uintToBytes(long value, int numBytes) {
    int longBytes = Long.SIZE / 8;
    Bytes valueBytes = Bytes.ofUnsignedLong(value, ByteOrder.LITTLE_ENDIAN);
    if (numBytes <= longBytes) {
      return valueBytes.slice(0, numBytes);
    } else {
      return Bytes.wrap(valueBytes, Bytes.wrap(new byte[numBytes - longBytes]));
    }
  }

  public int getBeaconProposerIndex(BeaconState state) {
    return getBeaconProposerIndex(state, state.getSlot());
  }

  public int getBeaconProposerIndex(BeaconState state, UInt64 requestedSlot) {
    validateStateCanCalculateProposerIndexAtSlot(state, requestedSlot);
    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconProposerIndex()
        .get(
            requestedSlot,
            slot -> {
              UInt64 epoch = computeEpochAtSlot(slot);
              Bytes32 seed =
                  Hash.sha2_256(
                      Bytes.concatenate(
                          getSeed(state, epoch, specConstants.getDomainBeaconProposer()),
                          uintToBytes(slot.longValue(), 8)));
              List<Integer> indices = get_active_validator_indices(state, epoch);
              return spec.getCommitteeUtil().computeProposerIndex(state, indices, seed);
            });
  }

  public Bytes32 computeDomain(Bytes4 domainType) {
    return computeDomain(domainType, specConstants.getGenesisForkVersion(), Bytes32.ZERO);
  }

  public Bytes32 getPreviousDutyDependentRoot(BeaconState state) {
    return getDutyDependentRoot(state, getPreviousEpoch(state));
  }

  public Bytes32 getCurrentDutyDependentRoot(BeaconState state) {
    return getDutyDependentRoot(state, getCurrentEpoch(state));
  }

  public Bytes4 computeForkDigest(Bytes4 currentVersion, Bytes32 genesisValidatorsRoot) {
    return new Bytes4(computeForkDataRoot(currentVersion, genesisValidatorsRoot).slice(0, 4));
  }

  public Bytes32 getDomain(BeaconState state, Bytes4 domainType, UInt64 messageEpoch) {
    UInt64 epoch = (messageEpoch == null) ? getCurrentEpoch(state) : messageEpoch;
    return getDomain(domainType, epoch, state.getFork(), state.getGenesis_validators_root());
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
    final UInt64 slot = computeStartSlotAtEpoch(epoch).minusMinZero(1);
    return slot.equals(state.getSlot())
        // No previous block, use algorithm for calculating the genesis block root
        ? BeaconBlock.fromGenesisState(state).getRoot()
        : getBlockRootAtSlot(state, slot);
  }

  private void validateStateCanCalculateProposerIndexAtSlot(
      final BeaconState state, final UInt64 requestedSlot) {
    UInt64 epoch = computeEpochAtSlot(requestedSlot);
    final UInt64 stateEpoch = getCurrentEpoch(state);
    checkArgument(
        epoch.equals(stateEpoch),
        "Cannot calculate proposer index for a slot outside the current epoch. Requested slot %s (in epoch %s), state slot %s (in epoch %s)",
        requestedSlot,
        epoch,
        state.getSlot(),
        stateEpoch);
  }

  private boolean isBlockRootAvailableFromState(BeaconState state, UInt64 slot) {
    UInt64 slotPlusHistoricalRoot = slot.plus(specConstants.getSlotsPerHistoricalRoot());
    return slot.isLessThan(state.getSlot())
        && state.getSlot().isLessThanOrEqualTo(slotPlusHistoricalRoot);
  }
}
