/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class Constants {
  // TODO: Update config.toml setting of constants for 0.5, non-existing
  //  getter functions are purposefully being used here, so that we would
  //  need to create their actual getters before being able to run succesfully.

  // Misc
  public static int SHARD_COUNT = 1024; // 2^10 shards
  public static int TARGET_COMMITTEE_SIZE = 128; // 2^7 validators
  public static int MAX_INDICES_PER_ATTESTATION = 4096; // 2^5
  public static int MIN_PER_EPOCH_CHURN_LIMIT = 4; // 2^2 withdrawals
  public static int CHURN_LIMIT_QUOTIENT = 65536; // 2^2 withdrawals
  public static int BASE_REWARDS_PER_EPOCH = 5; // 2^2 withdrawals
  public static int SHUFFLE_ROUND_COUNT = 90;

  // Deposit contract
  public static int DEPOSIT_CONTRACT_TREE_DEPTH = 32; // 2^5

  // Gwei values
  public static long MIN_DEPOSIT_AMOUNT = 1000000000L; // 2^0 * 1E9 Gwei
  public static long MAX_EFFECTIVE_BALANCE = 32000000000L; // 2^5 * 1E9 Gwei
  public static long EJECTION_BALANCE = 16000000000L; // 2^4 * 1E9 Gwei
  public static long EFFECTIVE_BALANCE_INCREMENT = 1000000000L; // 2^0 * 1E9 Gwei

  // Initial values
  public static long GENESIS_SLOT = 0; // 2^32
  public static long GENESIS_EPOCH = 0;
  public static long GENESIS_FORK_VERSION = 0;
  public static UnsignedLong FAR_FUTURE_EPOCH = UnsignedLong.MAX_VALUE;
  public static int BLS_WITHDRAWAL_PREFIX = 0;

  // Time parameters
  public static int MIN_ATTESTATION_INCLUSION_DELAY = 1; // 2^0 slots
  public static int SLOTS_PER_EPOCH = 64; // 2^6 slots
  public static int MIN_SEED_LOOKAHEAD = 1; // 2^0 epochs (6.4 minutes)
  public static int ACTIVATION_EXIT_DELAY = 4; // 2^2 epochs (25.6 minutes)
  public static int SLOTS_PER_ETH1_VOTING_PERIOD = 1024; // 2^4 epochs (~1.7 hours)
  public static int SLOTS_PER_HISTORICAL_ROOT = 8192; // 2^13 slots (~13 hours)
  public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY = 256; // 2^8 epochs (~27 hours)
  public static int PERSISTENT_COMMITTEE_PERIOD = 2048; // 2^11 epochs (~9 days)
  public static int MAX_EPOCHS_PER_CROSSLINK = 64; // 2^11 epochs (~9 days)
  public static int MIN_EPOCHS_TO_INACTIVITY_PENALTY = 4; // 2^11 epochs (~9 days)

  public static int SECONDS_PER_SLOT =
      6; // removed in 7.1 main spec for some reason but keep for now

  // Reward and penalty quotients
  public static int BASE_REWARD_FACTOR = 64; // 2^6
  public static int WHISTLEBLOWING_REWARD_QUOTIENT = 512; // 2^9
  public static int PROPOSER_REWARD_QUOTIENT = 8; // 2^3
  public static int INACTIVITY_PENALTY_QUOTIENT = 33554432; // 2^25
  public static int MIN_SLASHING_PENALTY_QUOTIENT = 32; // 2^5

  // Max transactions per block
  public static int MAX_PROPOSER_SLASHINGS = 16; // 2^4
  public static int MAX_ATTESTER_SLASHINGS = 1; // 2^0
  public static int MAX_ATTESTATIONS = 128; // 2^7
  public static int MAX_DEPOSITS = 16; // 2^4
  public static int MAX_VOLUNTARY_EXITS = 16; // 2^4
  public static int MAX_TRANSFERS = 16; // 2^4

  // Signature domains
  public static int DOMAIN_BEACON_PROPOSER = 0;
  public static int DOMAIN_RANDAO = 1;
  public static int DOMAIN_ATTESTATION = 2;
  public static int DOMAIN_DEPOSIT = 3;
  public static int DOMAIN_VOLUNTARY_EXIT = 4;
  public static int DOMAIN_TRANSFER = 5;

  // Artemis specific
  public static String SIM_DEPOSIT_VALUE = "1000000000000000000";
  public static int DEPOSIT_DATA_SIZE = 512; //
  public static int VALIDATOR_CLIENT_PORT_BASE = 50000;

  // Deposit
  public static String DEPOSIT_NORMAL = "normal";
  public static String DEPOSIT_TEST = "test";
  public static String DEPOSIT_SIM = "simulation";

  public static BLSSignature EMPTY_SIGNATURE = BLSSignature.empty();

  public static void init(ArtemisConfiguration config) {
    // Misc
    SHARD_COUNT =
        config.getShardCount() != Integer.MIN_VALUE
            ? config.getShardCount()
            : SHARD_COUNT; // 2^10 shards
    TARGET_COMMITTEE_SIZE =
        config.getTargetCommitteeSize() != Integer.MIN_VALUE
            ? config.getTargetCommitteeSize()
            : TARGET_COMMITTEE_SIZE; // 2^7 validators
    MAX_INDICES_PER_ATTESTATION =
        config.getMaxIndicesPerAttestation() != Integer.MIN_VALUE
            ? config.getMaxIndicesPerAttestation()
            : MAX_INDICES_PER_ATTESTATION; // 2^7 validators
    CHURN_LIMIT_QUOTIENT =
        config.getChurnLimitQuotient() != Integer.MIN_VALUE
            ? config.getChurnLimitQuotient()
            : CHURN_LIMIT_QUOTIENT; // 2^7 validators
    BASE_REWARDS_PER_EPOCH =
        config.getBaseRewardsPerEpoch() != Integer.MIN_VALUE
            ? config.getBaseRewardsPerEpoch()
            : BASE_REWARDS_PER_EPOCH; // 2^7 validators
    SHUFFLE_ROUND_COUNT =
        config.getShuffleRoundCount() != Integer.MIN_VALUE
            ? config.getShuffleRoundCount()
            : SHUFFLE_ROUND_COUNT;

    DEPOSIT_CONTRACT_TREE_DEPTH =
        config.getDepositContractTreeDepth() != Integer.MIN_VALUE
            ? config.getDepositContractTreeDepth()
            : DEPOSIT_CONTRACT_TREE_DEPTH; // 2^5

    // Gwei values
    MIN_DEPOSIT_AMOUNT =
        config.getMinDepositAmount() != Long.MIN_VALUE
            ? config.getMinDepositAmount()
            : MIN_DEPOSIT_AMOUNT; // 2^0 * 1E9 Gwei
    MAX_EFFECTIVE_BALANCE =
        config.getMaxEffectiveBalance() != Long.MIN_VALUE
            ? config.getMaxEffectiveBalance()
            : MAX_EFFECTIVE_BALANCE; // 2^7 validators
    EJECTION_BALANCE =
        config.getEjectionBalance() != Long.MIN_VALUE
            ? config.getEjectionBalance()
            : EJECTION_BALANCE; // 2^4 * 1E9 Gwei
    EFFECTIVE_BALANCE_INCREMENT =
        config.getEffectiveBalanceIncrement() != Long.MIN_VALUE
            ? config.getEffectiveBalanceIncrement()
            : EFFECTIVE_BALANCE_INCREMENT; // 2^4 * 1E9 Gwei

    // Time parameters
    SECONDS_PER_SLOT =
        config.getSecondsPerSlot() != Integer.MIN_VALUE
            ? config.getSecondsPerSlot()
            : SECONDS_PER_SLOT; // 6 seconds
    MIN_ATTESTATION_INCLUSION_DELAY =
        config.getMinAttestationInclusionDelay() != Integer.MIN_VALUE
            ? config.getMinAttestationInclusionDelay()
            : MIN_ATTESTATION_INCLUSION_DELAY; // 2^2 slots
    SLOTS_PER_EPOCH =
        config.getSlotsPerEpoch() != Integer.MIN_VALUE
            ? config.getSlotsPerEpoch()
            : SLOTS_PER_EPOCH; // 2^6 slots
    MIN_SEED_LOOKAHEAD =
        config.getMinSeedLookahead() != Integer.MIN_VALUE
            ? config.getMinSeedLookahead()
            : MIN_SEED_LOOKAHEAD; // 2^0 epochs (6.4 minutes)
    ACTIVATION_EXIT_DELAY =
        config.getActivationExitDelay() != Integer.MIN_VALUE
            ? config.getActivationExitDelay()
            : ACTIVATION_EXIT_DELAY; // 2^2 epochs (25.6 minutes)
    SLOTS_PER_ETH1_VOTING_PERIOD =
        config.getSlotsPerEth1VotingPeriod() != Integer.MIN_VALUE
            ? config.getSlotsPerEth1VotingPeriod()
            : SLOTS_PER_ETH1_VOTING_PERIOD;
    SLOTS_PER_HISTORICAL_ROOT =
        config.getSlotsPerHistoricalRoot() != Integer.MIN_VALUE
            ? config.getSlotsPerHistoricalRoot()
            : SLOTS_PER_HISTORICAL_ROOT;
    MIN_VALIDATOR_WITHDRAWABILITY_DELAY =
        config.getMinValidatorWithdrawabilityDelay() != Integer.MIN_VALUE
            ? config.getMinValidatorWithdrawabilityDelay()
            : MIN_VALIDATOR_WITHDRAWABILITY_DELAY; // 2^8 epochs (~27 hours)
    PERSISTENT_COMMITTEE_PERIOD =
        config.getPersistentCommitteePeriod() != Integer.MIN_VALUE
            ? config.getPersistentCommitteePeriod()
            : PERSISTENT_COMMITTEE_PERIOD;
    MAX_EPOCHS_PER_CROSSLINK =
        config.getMaxEpochsPerCrosslink() != Integer.MIN_VALUE
            ? config.getMaxEpochsPerCrosslink()
            : MAX_EPOCHS_PER_CROSSLINK;
    MIN_EPOCHS_TO_INACTIVITY_PENALTY =
        config.getMinEpochsToInactivityPenalty() != Integer.MIN_VALUE
            ? config.getMinEpochsToInactivityPenalty()
            : MIN_EPOCHS_TO_INACTIVITY_PENALTY;

    // Initial values
    GENESIS_SLOT =
        config.getGenesisSlot() != Long.MIN_VALUE ? config.getGenesisSlot() : GENESIS_SLOT; // 2^32
    FAR_FUTURE_EPOCH =
        config.getFarFutureEpoch() != Long.MAX_VALUE
            ? UnsignedLong.valueOf(config.getFarFutureEpoch())
            : FAR_FUTURE_EPOCH;
    BLS_WITHDRAWAL_PREFIX =
        config.getBlsWithdrawalPrefix() != 0
            ? config.getBlsWithdrawalPrefix()
            : BLS_WITHDRAWAL_PREFIX;

    // Reward and penalty quotients
    BASE_REWARD_FACTOR =
        config.getBaseRewardFactor() != Integer.MIN_VALUE
            ? config.getBaseRewardFactor()
            : BASE_REWARD_FACTOR; // 2^5
    WHISTLEBLOWING_REWARD_QUOTIENT =
        config.getWhistleblowingRewardQuotient() != Integer.MIN_VALUE
            ? config.getWhistleblowingRewardQuotient()
            : WHISTLEBLOWING_REWARD_QUOTIENT; // 2^9
    PROPOSER_REWARD_QUOTIENT =
        config.getProposerRewardQuotient() != Integer.MIN_VALUE
            ? config.getProposerRewardQuotient()
            : PROPOSER_REWARD_QUOTIENT; // 2^3
    INACTIVITY_PENALTY_QUOTIENT =
        config.getInactivityPenaltyQuotient() != Integer.MIN_VALUE
            ? config.getInactivityPenaltyQuotient()
            : INACTIVITY_PENALTY_QUOTIENT; // 2^24
    MIN_SLASHING_PENALTY_QUOTIENT =
        config.getMinSlashingPenaltyQuotient() != Integer.MIN_VALUE
            ? config.getMinSlashingPenaltyQuotient()
            : MIN_SLASHING_PENALTY_QUOTIENT; // 2^5

    // Max transactions per block
    MAX_PROPOSER_SLASHINGS =
        config.getMaxProposerSlashings() != Integer.MIN_VALUE
            ? config.getMaxProposerSlashings()
            : MAX_PROPOSER_SLASHINGS; // 2^4
    MAX_ATTESTER_SLASHINGS =
        config.getMaxAttesterSlashings() != Integer.MIN_VALUE
            ? config.getMaxAttesterSlashings()
            : MAX_ATTESTER_SLASHINGS; // 2^0
    MAX_ATTESTATIONS =
        config.getMaxAttestations() != Integer.MIN_VALUE
            ? config.getMaxAttestations()
            : MAX_ATTESTATIONS; // 2^7
    MAX_DEPOSITS =
        config.getMaxDeposits() != Integer.MIN_VALUE
            ? config.getMaxDeposits()
            : MAX_DEPOSITS; // 2^4
    MAX_VOLUNTARY_EXITS =
        config.getMaxVoluntaryExits() != Integer.MIN_VALUE
            ? config.getMaxVoluntaryExits()
            : MAX_VOLUNTARY_EXITS; // 2^4
    MAX_TRANSFERS =
        config.getMaxTransfers() != Integer.MIN_VALUE
            ? config.getMaxTransfers()
            : MAX_TRANSFERS; // 2^4

    // Signature domains
    DOMAIN_BEACON_PROPOSER =
        config.getDomainBeaconProposer() != Integer.MIN_VALUE
            ? config.getDomainBeaconProposer()
            : DOMAIN_BEACON_PROPOSER;
    DOMAIN_RANDAO =
        config.getDomainRandao() != Integer.MIN_VALUE ? config.getDomainRandao() : DOMAIN_RANDAO;
    DOMAIN_ATTESTATION =
        config.getDomainAttestation() != Integer.MIN_VALUE
            ? config.getDomainAttestation()
            : DOMAIN_ATTESTATION;
    DOMAIN_DEPOSIT =
        config.getDomainDeposit() != Integer.MIN_VALUE ? config.getDomainDeposit() : DOMAIN_DEPOSIT;
    DOMAIN_VOLUNTARY_EXIT =
        config.getDomainVoluntaryExit() != Integer.MIN_VALUE
            ? config.getDomainVoluntaryExit()
            : DOMAIN_VOLUNTARY_EXIT;
    DOMAIN_TRANSFER =
        config.getDomainTransfer() != Integer.MIN_VALUE
            ? config.getDomainTransfer()
            : DOMAIN_TRANSFER;

    // Artemis specific
    SIM_DEPOSIT_VALUE =
        !config.getSimDepositValue().equals("") ? config.getSimDepositValue() : SIM_DEPOSIT_VALUE;
    DEPOSIT_DATA_SIZE =
        config.getDepositDataSize() != Integer.MIN_VALUE
            ? config.getDepositDataSize()
            : DEPOSIT_DATA_SIZE;
  }

  /**
   * Return the epoch number of the given ``slot``.
   *
   * @param slot
   * @return
   */
  public static long slot_to_epoch(long slot) {
    return slot / SLOTS_PER_EPOCH;
  }
}
