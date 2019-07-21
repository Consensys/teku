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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes;

public class Constants {

  // Non-configurable constants
  public static UnsignedLong FAR_FUTURE_EPOCH = UnsignedLong.MAX_VALUE;
  public static int BASE_REWARDS_PER_EPOCH = 5;
  public static int DEPOSIT_CONTRACT_TREE_DEPTH = 32;
  public static int SECONDS_PER_DAY = 86400;
  public static int JUSTIFICATION_BITS_LENGTH = 4;
  public static String ENDIANNESS = "little";

  // Misc
  public static int SHARD_COUNT = 1024;
  public static int TARGET_COMMITTEE_SIZE = 128;
  public static int MAX_VALIDATORS_PER_COMMITTEE = 4096;
  public static int MIN_PER_EPOCH_CHURN_LIMIT = 4;
  public static int CHURN_LIMIT_QUOTIENT = 65536;
  public static int SHUFFLE_ROUND_COUNT = 90;
  public static int MIN_GENESIS_ACTIVE_VALIDATOR_COUNT = 65536;
  public static UnsignedLong MIN_GENESIS_TIME = UnsignedLong.valueOf(1578009600);

  // Gwei values
  public static long MIN_DEPOSIT_AMOUNT = 1000000000L;
  public static long MAX_EFFECTIVE_BALANCE = 32000000000L;
  public static long EJECTION_BALANCE = 16000000000L;
  public static long EFFECTIVE_BALANCE_INCREMENT = 1000000000L;

  // Initial values
  public static long GENESIS_SLOT = 0;
  public static long GENESIS_EPOCH = 0;
  public static Bytes BLS_WITHDRAWAL_PREFIX = Bytes.wrap(new byte[1]);

  // Time parameters
  public static int MIN_ATTESTATION_INCLUSION_DELAY = 1;
  public static int SLOTS_PER_EPOCH = 64;
  public static int MIN_SEED_LOOKAHEAD = 1;
  public static int ACTIVATION_EXIT_DELAY = 4;
  public static int SLOTS_PER_ETH1_VOTING_PERIOD = 1024;
  public static int SLOTS_PER_HISTORICAL_ROOT = 8192;
  public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY = 256;
  public static int PERSISTENT_COMMITTEE_PERIOD = 2048;
  public static int MAX_EPOCHS_PER_CROSSLINK = 64;
  public static int MIN_EPOCHS_TO_INACTIVITY_PENALTY = 4;

  // State list lengths
  public static int EPOCHS_PER_HISTORICAL_VECTOR = 65536;
  public static int EPOCHS_PER_SLASHINGS_VECTOR = 8192;
  public static int HISTORICAL_ROOTS_LIMIT = 16777216;
  public static UnsignedLong VALIDATOR_REGISTRY_LIMIT = UnsignedLong.valueOf(1099511627776L);

  // Reward and penalty quotients
  public static int BASE_REWARD_FACTOR = 64;
  public static int WHISTLEBLOWER_REWARD_QUOTIENT = 512;
  public static int PROPOSER_REWARD_QUOTIENT = 8;
  public static int INACTIVITY_PENALTY_QUOTIENT = 33554432;
  public static int MIN_SLASHING_PENALTY_QUOTIENT = 32;

  // Max transactions per block
  public static int MAX_PROPOSER_SLASHINGS = 16;
  public static int MAX_ATTESTER_SLASHINGS = 1;
  public static int MAX_ATTESTATIONS = 128;
  public static int MAX_DEPOSITS = 16;
  public static int MAX_VOLUNTARY_EXITS = 16;
  public static int MAX_TRANSFERS = 16;

  // Signature domains
  public static Bytes DOMAIN_BEACON_PROPOSER = int_to_bytes(0, 4);
  public static Bytes DOMAIN_RANDAO = int_to_bytes(1, 4);
  public static Bytes DOMAIN_ATTESTATION = int_to_bytes(2, 4);
  public static Bytes DOMAIN_DEPOSIT = int_to_bytes(3, 4);
  public static Bytes DOMAIN_VOLUNTARY_EXIT = int_to_bytes(4, 4);
  public static Bytes DOMAIN_TRANSFER = int_to_bytes(5, 4);

  // Artemis specific
  public static String SIM_DEPOSIT_VALUE = "1000000000000000000";
  public static int DEPOSIT_DATA_SIZE = 512; //
  public static int VALIDATOR_CLIENT_PORT_BASE = 50000;
  public static Bytes32 ZERO_HASH = Bytes32.ZERO;
  public static int SECONDS_PER_SLOT = 6;
  public static double TIME_TICKER_REFRESH_RATE = 2; // per sec
  public static UnsignedLong GENESIS_TIME = UnsignedLong.MAX_VALUE;
  public static UnsignedLong GENESIS_START_DELAY = UnsignedLong.valueOf(5);
  // TODO make this variable through yaml

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
                    : SHARD_COUNT;

    TARGET_COMMITTEE_SIZE =
            config.getTargetCommitteeSize() != Integer.MIN_VALUE
                    ? config.getTargetCommitteeSize()
                    : TARGET_COMMITTEE_SIZE;
    MAX_VALIDATORS_PER_COMMITTEE =
            config.getMaxValidatorsPerCommittee() != Integer.MIN_VALUE
                    ? config.getMaxValidatorsPerCommittee()
                    : MAX_VALIDATORS_PER_COMMITTEE;
    MIN_PER_EPOCH_CHURN_LIMIT =
            config.getMinPerEpochChurnLimit() != Integer.MIN_VALUE
                    ? config.getMinPerEpochChurnLimit()
                    : MIN_PER_EPOCH_CHURN_LIMIT;
    CHURN_LIMIT_QUOTIENT =
            config.getChurnLimitQuotient() != Integer.MIN_VALUE
                    ? config.getChurnLimitQuotient()
                    : CHURN_LIMIT_QUOTIENT; // 2^7 validators
    SHUFFLE_ROUND_COUNT =
            config.getShuffleRoundCount() != Integer.MIN_VALUE
                    ? config.getShuffleRoundCount()
                    : SHUFFLE_ROUND_COUNT;
    MIN_GENESIS_ACTIVE_VALIDATOR_COUNT =
        config.getMinGenesisActiveValidatorCount() != Integer.MIN_VALUE
            ? config.getShuffleRoundCount()
            : MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
    MIN_GENESIS_TIME =
        config.getMinGenesisTime() != Integer.MIN_VALUE
            ? UnsignedLong.valueOf(config.getMinGenesisTime())
            : MIN_GENESIS_TIME;

    // Gwei values
    MIN_DEPOSIT_AMOUNT =
            config.getMinDepositAmount() != Long.MIN_VALUE
                    ? config.getMinDepositAmount()
                    : MIN_DEPOSIT_AMOUNT;
    MAX_EFFECTIVE_BALANCE =
            config.getMaxEffectiveBalance() != Long.MIN_VALUE
                    ? config.getMaxEffectiveBalance()
                    : MAX_EFFECTIVE_BALANCE;
    EJECTION_BALANCE =
            config.getEjectionBalance() != Long.MIN_VALUE
                    ? config.getEjectionBalance()
                    : EJECTION_BALANCE;
    EFFECTIVE_BALANCE_INCREMENT =
            config.getEffectiveBalanceIncrement() != Long.MIN_VALUE
                    ? config.getEffectiveBalanceIncrement()
                    : EFFECTIVE_BALANCE_INCREMENT;

    // Initial values
    GENESIS_SLOT =
            config.getGenesisSlot() != Long.MIN_VALUE ? config.getGenesisSlot() : GENESIS_SLOT;
    GENESIS_EPOCH =
        config.getGenesisEpoch() != Long.MIN_VALUE ? config.getGenesisEpoch() : GENESIS_SLOT;
    BLS_WITHDRAWAL_PREFIX =
            !config.getBlsWithdrawalPrefix().equals("")
                    ? Bytes.fromHexString(config.getBlsWithdrawalPrefix())
                    : BLS_WITHDRAWAL_PREFIX;

    // Time parameters
    MIN_ATTESTATION_INCLUSION_DELAY =
            config.getMinAttestationInclusionDelay() != Integer.MIN_VALUE
                    ? config.getMinAttestationInclusionDelay()
                    : MIN_ATTESTATION_INCLUSION_DELAY;
    SLOTS_PER_EPOCH =
            config.getSlotsPerEpoch() != Integer.MIN_VALUE
                    ? config.getSlotsPerEpoch()
                    : SLOTS_PER_EPOCH;
    MIN_SEED_LOOKAHEAD =
            config.getMinSeedLookahead() != Integer.MIN_VALUE
                    ? config.getMinSeedLookahead()
                    : MIN_SEED_LOOKAHEAD;
    ACTIVATION_EXIT_DELAY =
            config.getActivationExitDelay() != Integer.MIN_VALUE
                    ? config.getActivationExitDelay()
                    : ACTIVATION_EXIT_DELAY;
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
                    : MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
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

    // State list lengths
    EPOCHS_PER_HISTORICAL_VECTOR =
        config.getEpochsPerHistoricalVector() != Integer.MIN_VALUE
            ? config.getEpochsPerHistoricalVector()
            : EPOCHS_PER_HISTORICAL_VECTOR;
    EPOCHS_PER_SLASHINGS_VECTOR =
        config.getEpochsPerSlashingsVector() != Integer.MIN_VALUE
            ? config.getEpochsPerSlashingsVector()
            : EPOCHS_PER_SLASHINGS_VECTOR;
    HISTORICAL_ROOTS_LIMIT =
        config.getHistoricalRootsLimit() != Integer.MIN_VALUE
            ? config.getHistoricalRootsLimit()
            : HISTORICAL_ROOTS_LIMIT;
    VALIDATOR_REGISTRY_LIMIT =
            config.getValidatorRegistryLimit() != Long.MIN_VALUE
                    ? UnsignedLong.valueOf(config.getValidatorRegistryLimit())
                    : VALIDATOR_REGISTRY_LIMIT;
    // Rewards and penalties
    BASE_REWARD_FACTOR =
            config.getBaseRewardFactor() != Integer.MIN_VALUE
                    ? config.getBaseRewardFactor()
                    : BASE_REWARD_FACTOR;
    WHISTLEBLOWER_REWARD_QUOTIENT =
            config.getWhistleblowerRewardQuotient() != Integer.MIN_VALUE
                    ? config.getWhistleblowerRewardQuotient()
                    : WHISTLEBLOWER_REWARD_QUOTIENT;
    PROPOSER_REWARD_QUOTIENT =
            config.getProposerRewardQuotient() != Integer.MIN_VALUE
                    ? config.getProposerRewardQuotient()
                    : PROPOSER_REWARD_QUOTIENT;
    INACTIVITY_PENALTY_QUOTIENT =
            config.getInactivityPenaltyQuotient() != Integer.MIN_VALUE
                    ? config.getInactivityPenaltyQuotient()
                    : INACTIVITY_PENALTY_QUOTIENT;
    MIN_SLASHING_PENALTY_QUOTIENT =
            config.getMinSlashingPenaltyQuotient() != Integer.MIN_VALUE
                    ? config.getMinSlashingPenaltyQuotient()
                    : MIN_SLASHING_PENALTY_QUOTIENT;

    // Max operations per block
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

    /*
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
    */

    // Artemis specific
    SIM_DEPOSIT_VALUE =
            !config.getSimDepositValue().equals("") ? config.getSimDepositValue() : SIM_DEPOSIT_VALUE;
    DEPOSIT_DATA_SIZE =
            config.getDepositDataSize() != Integer.MIN_VALUE
                    ? config.getDepositDataSize()
                    : DEPOSIT_DATA_SIZE;
    SECONDS_PER_SLOT =
            config.getSecondsPerSlot() != Integer.MIN_VALUE
                    ? config.getSecondsPerSlot()
                    : SECONDS_PER_SLOT; // 6 seconds
  }
}
