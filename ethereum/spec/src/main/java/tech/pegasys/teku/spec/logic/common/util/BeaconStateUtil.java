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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uintToBytes;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkData;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.util.GenesisGenerator;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.ssz.Merkleizable;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

@SuppressWarnings("unused")
public class BeaconStateUtil {

  private static final Logger LOG = LogManager.getLogger();
  /**
   * For debug/test purposes only enables/disables {@link DepositData} BLS signature verification
   * Setting to <code>false</code> significantly speeds up state initialization
   */
  public final boolean BLS_VERIFY_DEPOSIT = true;

  private final SpecConfig specConfig;
  private final SchemaDefinitions schemaDefinitions;
  private final ValidatorsUtil validatorsUtil;
  private final CommitteeUtil committeeUtil;

  private final Predicates predicates;
  private final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  public BeaconStateUtil(
      final SpecConfig specConfig,
      final SchemaDefinitions schemaDefinitions,
      final ValidatorsUtil validatorsUtil,
      final CommitteeUtil committeeUtil,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.validatorsUtil = validatorsUtil;
    this.committeeUtil = committeeUtil;
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
    return computeStartSlotAtEpoch(currentEpoch).equals(slot) ? currentEpoch : currentEpoch.plus(1);
  }

  public Bytes32 getBlockRootAtSlot(BeaconState state, UInt64 slot)
      throws IllegalArgumentException {
    checkArgument(
        isBlockRootAvailableFromState(state, slot),
        "Block at slot %s not available from state at slot %s",
        slot,
        state.getSlot());
    int latestBlockRootIndex = slot.mod(specConfig.getSlotsPerHistoricalRoot()).intValue();
    return state.getBlock_roots().getElement(latestBlockRootIndex);
  }

  public Bytes32 getBlockRoot(BeaconState state, UInt64 epoch) throws IllegalArgumentException {
    return getBlockRootAtSlot(state, computeStartSlotAtEpoch(epoch));
  }

  public UInt64 computeStartSlotAtEpoch(UInt64 epoch) {
    return epoch.times(specConfig.getSlotsPerEpoch());
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
              UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
              Bytes32 seed =
                  Hash.sha2_256(
                      Bytes.concatenate(
                          beaconStateAccessors.getSeed(
                              state, epoch, specConfig.getDomainBeaconProposer()),
                          uintToBytes(slot.longValue(), 8)));
              List<Integer> indices = beaconStateAccessors.getActiveValidatorIndices(state, epoch);
              return committeeUtil.computeProposerIndex(state, indices, seed);
            });
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

  public void initiateValidatorExit(MutableBeaconState state, int index) {
    Validator validator = state.getValidators().get(index);
    // Return if validator already initiated exit
    if (!validator.getExit_epoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }

    // Compute exit queue epoch
    List<UInt64> exit_epochs =
        state.getValidators().stream()
            .map(Validator::getExit_epoch)
            .filter(exitEpoch -> !exitEpoch.equals(FAR_FUTURE_EPOCH))
            .collect(toList());
    exit_epochs.add(computeActivationExitEpoch(beaconStateAccessors.getCurrentEpoch(state)));
    UInt64 exit_queue_epoch = Collections.max(exit_epochs);
    final UInt64 final_exit_queue_epoch = exit_queue_epoch;
    UInt64 exit_queue_churn =
        UInt64.valueOf(
            state.getValidators().stream()
                .filter(v -> v.getExit_epoch().equals(final_exit_queue_epoch))
                .count());

    if (exit_queue_churn.compareTo(getValidatorChurnLimit(state)) >= 0) {
      exit_queue_epoch = exit_queue_epoch.plus(UInt64.ONE);
    }

    // Set validator exit epoch and withdrawable epoch
    state
        .getValidators()
        .set(
            index,
            validator
                .withExit_epoch(exit_queue_epoch)
                .withWithdrawable_epoch(
                    exit_queue_epoch.plus(specConfig.getMinValidatorWithdrawabilityDelay())));
  }

  public Bytes computeSigningRoot(Merkleizable object, Bytes32 domain) {
    return new SigningData(object.hashTreeRoot(), domain).hashTreeRoot();
  }

  public Bytes computeSigningRoot(UInt64 number, Bytes32 domain) {
    SigningData domainWrappedObject = new SigningData(SszUInt64.of(number).hashTreeRoot(), domain);
    return domainWrappedObject.hashTreeRoot();
  }

  public UInt64 getValidatorChurnLimit(BeaconState state) {
    final int activeValidatorCount =
        beaconStateAccessors
            .getActiveValidatorIndices(state, beaconStateAccessors.getCurrentEpoch(state))
            .size();
    return getValidatorChurnLimit(activeValidatorCount);
  }

  public UInt64 getValidatorChurnLimit(final int activeValidatorCount) {
    return UInt64.valueOf(specConfig.getMinPerEpochChurnLimit())
        .max(UInt64.valueOf(activeValidatorCount / specConfig.getChurnLimitQuotient()));
  }

  public UInt64 computeActivationExitEpoch(UInt64 epoch) {
    return epoch.plus(UInt64.ONE).plus(specConfig.getMaxSeedLookahead());
  }

  public boolean all(SszBitvector bitvector, int start, int end) {
    for (int i = start; i < end; i++) {
      if (!bitvector.getBit(i)) {
        return false;
      }
    }
    return true;
  }

  public UInt64 getCommitteeCountPerSlot(BeaconState state, UInt64 epoch) {
    List<Integer> active_validator_indices =
        beaconStateAccessors.getActiveValidatorIndices(state, epoch);
    return UInt64.valueOf(
        Math.max(
            1,
            Math.min(
                specConfig.getMaxCommitteesPerSlot(),
                Math.floorDiv(
                    Math.floorDiv(active_validator_indices.size(), specConfig.getSlotsPerEpoch()),
                    specConfig.getTargetCommitteeSize()))));
  }

  public UInt64 getCommitteeCountPerSlot(final int activeValidatorCount) {
    return UInt64.valueOf(
        Math.max(
            1,
            Math.min(
                specConfig.getMaxCommitteesPerSlot(),
                Math.floorDiv(
                    Math.floorDiv(activeValidatorCount, specConfig.getSlotsPerEpoch()),
                    specConfig.getTargetCommitteeSize()))));
  }

  public void slashValidator(MutableBeaconState state, int slashed_index) {
    slashValidator(state, slashed_index, -1);
  }

  public UInt64 getAttestersTotalEffectiveBalance(final BeaconState state, final UInt64 slot) {
    validateStateForCommitteeQuery(state, slot);
    return BeaconStateCache.getTransitionCaches(state)
        .getAttestersTotalBalance()
        .get(
            slot,
            p -> {
              final SszList<Validator> validators = state.getValidators();
              final UInt64 committeeCount =
                  getCommitteeCountPerSlot(state, miscHelpers.computeEpochAtSlot(slot));
              return UInt64.range(UInt64.ZERO, committeeCount)
                  .flatMap(committee -> streamEffectiveBalancesForCommittee(state, slot, committee))
                  .reduce(UInt64.ZERO, UInt64::plus);
            });
  }

  private Stream<UInt64> streamEffectiveBalancesForCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 committeeIndex) {
    return getBeaconCommittee(state, slot, committeeIndex).stream()
        .map(validatorIndex -> state.getValidators().get(validatorIndex).getEffective_balance());
  }

  public List<Integer> getBeaconCommittee(BeaconState state, UInt64 slot, UInt64 index) {
    // Make sure state is within range of the slot being queried
    validateStateForCommitteeQuery(state, slot);

    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconCommittee()
        .get(
            TekuPair.of(slot, index),
            p -> {
              UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
              UInt64 committees_per_slot = getCommitteeCountPerSlot(state, epoch);
              int committeeIndex =
                  slot.mod(specConfig.getSlotsPerEpoch())
                      .times(committees_per_slot)
                      .plus(index)
                      .intValue();
              int count = committees_per_slot.times(specConfig.getSlotsPerEpoch()).intValue();
              return committeeUtil.computeCommittee(
                  state,
                  beaconStateAccessors.getActiveValidatorIndices(state, epoch),
                  beaconStateAccessors.getSeed(state, epoch, specConfig.getDomainBeaconAttester()),
                  committeeIndex,
                  count);
            });
  }

  public UInt64 getEarliestQueryableSlotForTargetEpoch(final UInt64 epoch) {
    final UInt64 previousEpoch = epoch.compareTo(UInt64.ZERO) > 0 ? epoch.minus(UInt64.ONE) : epoch;
    return computeStartSlotAtEpoch(previousEpoch);
  }

  private void validateStateForCommitteeQuery(BeaconState state, UInt64 slot) {
    final UInt64 oldestQueryableSlot = getEarliestQueryableSlotForTargetSlot(slot);
    checkArgument(
        state.getSlot().compareTo(oldestQueryableSlot) >= 0,
        "Committee information must be derived from a state no older than the previous epoch. State at slot %s is older than cutoff slot %s",
        state.getSlot(),
        oldestQueryableSlot);
  }

  private UInt64 getEarliestQueryableSlotForTargetSlot(final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    return getEarliestQueryableSlotForTargetEpoch(epoch);
  }

  private void slashValidator(MutableBeaconState state, int slashedIndex, int whistleblowerIndex) {
    UInt64 epoch = beaconStateAccessors.getCurrentEpoch(state);
    initiateValidatorExit(state, slashedIndex);

    Validator validator = state.getValidators().get(slashedIndex);

    state
        .getValidators()
        .set(
            slashedIndex,
            validator
                .withSlashed(true)
                .withWithdrawable_epoch(
                    validator
                        .getWithdrawable_epoch()
                        .max(epoch.plus(specConfig.getEpochsPerSlashingsVector()))));

    int index = epoch.mod(specConfig.getEpochsPerSlashingsVector()).intValue();
    state
        .getSlashings()
        .setElement(
            index, state.getSlashings().getElement(index).plus(validator.getEffective_balance()));
    validatorsUtil.decreaseBalance(
        state,
        slashedIndex,
        validator.getEffective_balance().dividedBy(specConfig.getMinSlashingPenaltyQuotient()));

    // Apply proposer and whistleblower rewards
    int proposer_index = getBeaconProposerIndex(state);
    if (whistleblowerIndex == -1) {
      whistleblowerIndex = proposer_index;
    }

    UInt64 whistleblower_reward =
        validator.getEffective_balance().dividedBy(specConfig.getWhistleblowerRewardQuotient());
    UInt64 proposer_reward = whistleblower_reward.dividedBy(specConfig.getProposerRewardQuotient());
    validatorsUtil.increaseBalance(state, proposer_index, proposer_reward);
    validatorsUtil.increaseBalance(
        state, whistleblowerIndex, whistleblower_reward.minus(proposer_reward));
  }

  public Bytes32 computeSigningRoot(Bytes bytes, Bytes32 domain) {
    SigningData domainWrappedObject =
        new SigningData(SszByteVector.computeHashTreeRoot(bytes), domain);
    return domainWrappedObject.hashTreeRoot();
  }

  public static boolean isValidMerkleBranch(
      Bytes32 leaf, SszBytes32Vector branch, int depth, int index, Bytes32 root) {
    Bytes32 value = leaf;
    for (int i = 0; i < depth; i++) {
      if (Math.floor(index / Math.pow(2, i)) % 2 == 1) {
        value = Hash.sha2_256(Bytes.concatenate(branch.getElement(i), value));
      } else {
        value = Hash.sha2_256(Bytes.concatenate(value, branch.getElement(i)));
      }
    }
    return value.equals(root);
  }

  public BeaconState initializeBeaconStateFromEth1(
      Bytes32 eth1_block_hash, UInt64 eth1_timestamp, List<? extends Deposit> deposits) {
    final GenesisGenerator genesisGenerator = new GenesisGenerator(schemaDefinitions);
    genesisGenerator.updateCandidateState(eth1_block_hash, eth1_timestamp, deposits);
    return genesisGenerator.getGenesisState();
  }

  public boolean isSlotAtNthEpochBoundary(
      final UInt64 blockSlot, final UInt64 parentSlot, final int n) {
    checkArgument(n > 0, "Parameter n must be greater than 0");
    final UInt64 blockEpoch = miscHelpers.computeEpochAtSlot(blockSlot);
    final UInt64 parentEpoch = miscHelpers.computeEpochAtSlot(parentSlot);
    return blockEpoch.dividedBy(n).isGreaterThan(parentEpoch.dividedBy(n));
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
        getCommitteeCountPerSlot(state, miscHelpers.computeEpochAtSlot(attestationSlot)));
  }

  public void processDeposit(MutableBeaconState state, Deposit deposit) {
    checkArgument(
        isValidMerkleBranch(
            deposit.getData().hashTreeRoot(),
            deposit.getProof(),
            specConfig.getDepositContractTreeDepth() + 1, // Add 1 for the List length mix-in
            toIntExact(state.getEth1_deposit_index().longValue()),
            state.getEth1_data().getDeposit_root()),
        "process_deposit: Verify the Merkle branch");

    processDepositWithoutCheckingMerkleProof(state, deposit, null);
  }

  void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Map<BLSPublicKey, Integer> pubKeyToIndexMap) {
    state.setEth1_deposit_index(state.getEth1_deposit_index().plus(UInt64.ONE));

    final BLSPublicKey pubkey = deposit.getData().getPubkey();
    final UInt64 amount = deposit.getData().getAmount();

    OptionalInt existingIndex;
    if (pubKeyToIndexMap != null) {
      final Integer cachedIndex =
          pubKeyToIndexMap.putIfAbsent(pubkey, state.getValidators().size());
      existingIndex = cachedIndex == null ? OptionalInt.empty() : OptionalInt.of(cachedIndex);
    } else {
      SszList<Validator> validators = state.getValidators();

      Function<Integer, BLSPublicKey> validatorPubkey =
          index ->
              beaconStateAccessors.getValidatorPubKey(state, UInt64.valueOf(index)).orElse(null);

      existingIndex =
          IntStream.range(0, validators.size())
              .filter(index -> pubkey.equals(validatorPubkey.apply(index)))
              .findFirst();
    }

    if (existingIndex.isEmpty()) {

      // Verify the deposit signature (proof of possession) which is not checked by the deposit
      // contract
      if (BLS_VERIFY_DEPOSIT) {
        final DepositMessage deposit_message =
            new DepositMessage(pubkey, deposit.getData().getWithdrawal_credentials(), amount);
        final Bytes32 domain = computeDomain(specConfig.getDomainDeposit());
        final Bytes signing_root = computeSigningRoot(deposit_message, domain);
        boolean proof_is_valid =
            !BLS_VERIFY_DEPOSIT
                || BLS.verify(pubkey, signing_root, deposit.getData().getSignature());
        if (!proof_is_valid) {
          if (deposit instanceof DepositWithIndex) {
            LOG.debug(
                "Skipping invalid deposit with index {} and pubkey {}",
                ((DepositWithIndex) deposit).getIndex(),
                pubkey);
          } else {
            LOG.debug("Skipping invalid deposit with pubkey {}", pubkey);
          }
          if (pubKeyToIndexMap != null) {
            // The validator won't be created so the calculated index won't be correct
            pubKeyToIndexMap.remove(pubkey);
          }
          return;
        }
      }

      if (pubKeyToIndexMap == null) {
        LOG.debug("Adding new validator to state: {}", state.getValidators().size());
      }
      state.getValidators().append(getValidatorFromDeposit(deposit));
      state.getBalances().appendElement(amount);
    } else {
      validatorsUtil.increaseBalance(state, existingIndex.getAsInt(), amount);
    }
  }

  private Validator getValidatorFromDeposit(Deposit deposit) {
    final UInt64 amount = deposit.getData().getAmount();
    final UInt64 effectiveBalance =
        amount
            .minus(amount.mod(specConfig.getEffectiveBalanceIncrement()))
            .min(specConfig.getMaxEffectiveBalance());
    return new Validator(
        deposit.getData().getPubkey(),
        deposit.getData().getWithdrawal_credentials(),
        effectiveBalance,
        false,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH);
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
        ? BeaconBlock.fromGenesisState(schemaDefinitions, state).getRoot()
        : getBlockRootAtSlot(state, slot);
  }

  private void validateStateCanCalculateProposerIndexAtSlot(
      final BeaconState state, final UInt64 requestedSlot) {
    UInt64 epoch = miscHelpers.computeEpochAtSlot(requestedSlot);
    final UInt64 stateEpoch = beaconStateAccessors.getCurrentEpoch(state);
    checkArgument(
        epoch.equals(stateEpoch),
        "Cannot calculate proposer index for a slot outside the current epoch. Requested slot %s (in epoch %s), state slot %s (in epoch %s)",
        requestedSlot,
        epoch,
        state.getSlot(),
        stateEpoch);
  }

  private boolean isBlockRootAvailableFromState(BeaconState state, UInt64 slot) {
    UInt64 slotPlusHistoricalRoot = slot.plus(specConfig.getSlotsPerHistoricalRoot());
    return slot.isLessThan(state.getSlot())
        && state.getSlot().isLessThanOrEqualTo(slotPlusHistoricalRoot);
  }
}
