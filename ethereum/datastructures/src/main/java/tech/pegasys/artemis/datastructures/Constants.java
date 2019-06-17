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
  public static int BASE_REWARD_PER_EPOCH = 5; // 2^2 withdrawals
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
  public static UnsignedLong FAR_FUTURE_EPOCH = UnsignedLong.MAX_VALUE;
  public static Bytes32 ZERO_HASH = Bytes32.ZERO; // TODO confirm if equals to b'\x00' * 32
  public static int BLS_WITHDRAWAL_PREFIX_BYTE = 0;

  // Time parameters
  public static int MIN_ATTESTATION_INCLUSION_DELAY = 4; // 2^2 slots
  public static int SLOTS_PER_EPOCH = 64; // 2^6 slots
  public static int MIN_SEED_LOOKAHEAD = 1; // 2^0 epochs (6.4 minutes)
  public static int ACTIVATION_EXIT_DELAY = 4; // 2^2 epochs (25.6 minutes)
  public static int SLOTS_PER_ETH1_VOTING_PERIOD = 1024; // 2^4 epochs (~1.7 hours)
  public static int SLOTS_PER_HISTORICAL_ROOT = 8192; // 2^13 slots (~13 hours)
  public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY = 256; // 2^8 epochs (~27 hours)
  public static int PERSISTENT_COMMITTEE_PERIOD = 2048; // 2^11 epochs (~9 days)
  public static int MAX_EPOCHS_PER_CROSSLINK = 64; // 2^11 epochs (~9 days)
  public static int MIN_EPOCHS_TO_INACTIVITY_PENALTY = 4; // 2^11 epochs (~9 days)

  public static int SECONDS_PER_SLOT = 6; // removed in 7.1 main spec for some reason but keep for now

  // State list lengths
  public static int LATEST_RANDAO_MIXES_LENGTH = 8192; // 2^13 epochs (~36 days)
  public static int LATEST_ACTIVE_INDEX_ROOTS_LENGTH = 8192; // 2^13 epochs (~36 days)
  public static int LATEST_SLASHED_EXIT_LENGTH = 8192; // 2^13 epochs (~36 days)

  // Reward and penalty quotients
  public static int BASE_REWARD_FACTOR = 32; // 2^5
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
    MAX_BALANCE_CHURN_QUOTIENT =
        config.getMaxBalanceChurnQuotient() != Integer.MIN_VALUE
            ? config.getMaxBalanceChurnQuotient()
            : MAX_BALANCE_CHURN_QUOTIENT; // 2^5
    MAX_INDICES_PER_SLASHABLE_VOTE =
        config.getMaxIndicesPerSlashableVote() != Integer.MIN_VALUE
            ? config.getMaxIndicesPerSlashableVote()
            : MAX_INDICES_PER_SLASHABLE_VOTE; // 2^12 votes
    MAX_EXIT_DEQUEUES_PER_EPOCH =
        config.getMaxExitDequeuesPerEpoch() != Integer.MIN_VALUE
            ? config.getMaxExitDequeuesPerEpoch()
            : MAX_EXIT_DEQUEUES_PER_EPOCH; // 2^2 withdrawals
    SHUFFLE_ROUND_COUNT =
        config.getShuffleRoundCount() != Integer.MIN_VALUE
            ? config.getShuffleRoundCount()
            : SHUFFLE_ROUND_COUNT;

    // Deposit contract
    DEPOSIT_CONTRACT_ADDRESS =
        !config.getDepositContractAddress().equals("")
            ? config.getDepositContractAddress()
            : DEPOSIT_CONTRACT_ADDRESS; // This is TBD in the spec.
    DEPOSIT_CONTRACT_TREE_DEPTH =
        config.getDepositContractTreeDepth() != Integer.MIN_VALUE
            ? config.getDepositContractTreeDepth()
            : DEPOSIT_CONTRACT_TREE_DEPTH; // 2^5

    // Gwei values
    MIN_DEPOSIT_AMOUNT =
        config.getMinDepositAmount() != Long.MIN_VALUE
            ? config.getMinDepositAmount()
            : MIN_DEPOSIT_AMOUNT; // 2^0 * 1E9 Gwei
    MAX_DEPOSIT_AMOUNT =
        config.getMaxDepositAmount() != Long.MIN_VALUE
            ? config.getMaxDepositAmount()
            : MAX_DEPOSIT_AMOUNT; // 2^5 * 1E9 Gwei
    FORK_CHOICE_BALANCE_INCREMENT =
        config.getForkChoiceBalanceIncrement() != Long.MIN_VALUE
            ? config.getForkChoiceBalanceIncrement()
            : FORK_CHOICE_BALANCE_INCREMENT;
    EJECTION_BALANCE =
        config.getEjectionBalance() != Long.MIN_VALUE
            ? config.getEjectionBalance()
            : EJECTION_BALANCE; // 2^4 * 1E9 Gwei

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
    EPOCHS_PER_ETH1_VOTING_PERIOD =
        config.getEpochsPerEth1VotingPeriod() != Integer.MIN_VALUE
            ? config.getEpochsPerEth1VotingPeriod()
            : EPOCHS_PER_ETH1_VOTING_PERIOD; // 2^4 epochs (~1.7 hours)
    SLOTS_PER_HISTORICAL_ROOT =
        config.getSlotsPerHistoricalRoot() != Integer.MIN_VALUE
            ? config.getSlotsPerHistoricalRoot()
            : SLOTS_PER_HISTORICAL_ROOT;
    MIN_VALIDATOR_WITHDRAWABILITY_DELAY =
        config.getMinValidatorWithdrawabilityDelay() != Integer.MIN_VALUE
            ? config.getMinValidatorWithdrawabilityDelay()
            : MIN_VALIDATOR_WITHDRAWABILITY_DELAY; // 2^8 epochs (~27 hours)

    // Initial values
    GENESIS_FORK_VERSION =
        config.getGenesisForkVersion() != Integer.MIN_VALUE
            ? config.getGenesisForkVersion()
            : GENESIS_FORK_VERSION;
    GENESIS_SLOT =
        config.getGenesisSlot() != Long.MIN_VALUE ? config.getGenesisSlot() : GENESIS_SLOT; // 2^32
    GENESIS_EPOCH =
        config.getGenesisEpoch() != Long.MIN_VALUE ? config.getGenesisEpoch() : GENESIS_EPOCH;
    GENESIS_START_SHARD =
        config.getGenesisStartShard() != Integer.MIN_VALUE
            ? config.getGenesisStartShard()
            : GENESIS_START_SHARD;
    FAR_FUTURE_EPOCH =
        config.getFarFutureEpoch() != Long.MAX_VALUE
            ? UnsignedLong.valueOf(config.getFarFutureEpoch())
            : FAR_FUTURE_EPOCH;
    ZERO_HASH =
        !config.getZeroHash().equals(Bytes32.ZERO)
            ? (Bytes32) config.getZeroHash()
            : ZERO_HASH; // TODO Verify
    EMPTY_SIGNATURE =
        !config.getEmptySignature().equals(BLSSignature.empty())
            ? (BLSSignature) config.getEmptySignature()
            : EMPTY_SIGNATURE;
    BLS_WITHDRAWAL_PREFIX_BYTE =
        !config.getBlsWithdrawalPrefixByte().equals(Bytes32.EMPTY)
            ? (Bytes32) config.getBlsWithdrawalPrefixByte()
            : BLS_WITHDRAWAL_PREFIX_BYTE; // TODO Verify

    // State list lengths
    LATEST_RANDAO_MIXES_LENGTH =
        config.getLatestRandaoMixesLength() != Integer.MIN_VALUE
            ? config.getLatestRandaoMixesLength()
            : LATEST_RANDAO_MIXES_LENGTH; // 2^13 epochs (~36 days)
    LATEST_ACTIVE_INDEX_ROOTS_LENGTH =
        config.getLatestActiveIndexRootsLength() != Integer.MIN_VALUE
            ? config.getLatestActiveIndexRootsLength()
            : LATEST_ACTIVE_INDEX_ROOTS_LENGTH; // 2^13 epochs (~36 days)
    LATEST_SLASHED_EXIT_LENGTH =
        config.getLatestSlashedExitLength() != Integer.MIN_VALUE
            ? config.getLatestSlashedExitLength()
            : LATEST_SLASHED_EXIT_LENGTH; // 2^13 epochs (~36 days)

    // Reward and penalty quotients
    BASE_REWARD_QUOTIENT =
        config.getBaseRewardQuotient() != Integer.MIN_VALUE
            ? config.getBaseRewardQuotient()
            : BASE_REWARD_QUOTIENT; // 2^5
    WHISTLEBLOWER_REWARD_QUOTIENT =
        config.getWhistleblowerRewardQuotient() != Integer.MIN_VALUE
            ? config.getWhistleblowerRewardQuotient()
            : WHISTLEBLOWER_REWARD_QUOTIENT; // 2^9
    ATTESTATION_INCLUSION_REWARD_QUOTIENT =
        config.getAttestationInclusionRewardQuotient() != Integer.MIN_VALUE
            ? config.getAttestationInclusionRewardQuotient()
            : ATTESTATION_INCLUSION_REWARD_QUOTIENT; // 2^3
    INACTIVITY_PENALTY_QUOTIENT =
        config.getInactivityPenaltyQuotient() != Integer.MIN_VALUE
            ? config.getInactivityPenaltyQuotient()
            : INACTIVITY_PENALTY_QUOTIENT; // 2^24
    MIN_PENALTY_QUOTIENT =
        config.getMinPenaltyQuotient() != Integer.MIN_VALUE
            ? config.getMinPenaltyQuotient()
            : MIN_PENALTY_QUOTIENT; // 2^5

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
    DOMAIN_BEACON_BLOCK =
        config.getDomainBeaconBlock() != Integer.MIN_VALUE
            ? config.getDomainBeaconBlock()
            : DOMAIN_BEACON_BLOCK;
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
