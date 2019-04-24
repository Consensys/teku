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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class Constants {
  // The constants below are correct as of spec v0.4

  // Misc
  public static int SHARD_COUNT = 1024; // 2^10 shards
  public static int TARGET_COMMITTEE_SIZE = 128; // 2^7 validators
  public static int MAX_BALANCE_CHURN_QUOTIENT = 32; // 2^5
  public static long BEACON_CHAIN_SHARD_NUMBER = -1L; // 2^64 - 1
  public static int MAX_INDICES_PER_SLASHABLE_VOTE = 4096; // 2^12 votes
  public static int MAX_EXIT_DEQUEUES_PER_EPOCH = 4; // 2^2 withdrawals
  public static int SHUFFLE_ROUND_COUNT = 90;

  // Deposit contract
  public static String DEPOSIT_CONTRACT_ADDRESS = "0x0"; // This is TBD in the spec.
  public static int DEPOSIT_CONTRACT_TREE_DEPTH = 32; // 2^5

  // Gwei values
  public static long MIN_DEPOSIT_AMOUNT = 1000000000L; // 2^0 * 1E9 Gwei
  public static long MAX_DEPOSIT_AMOUNT = 32000000000L; // 2^5 * 1E9 Gwei
  public static long FORK_CHOICE_BALANCE_INCREMENT = 1000000000L; // 2^0 * 1E9 Gwei
  public static long EJECTION_BALANCE = 16000000000L; // 2^4 * 1E9 Gwei

  // Time parameters
  public static int SECONDS_PER_SLOT = 6; // 6 seconds
  public static int MIN_ATTESTATION_INCLUSION_DELAY = 4; // 2^2 slots
  public static int SLOTS_PER_EPOCH = 64; // 2^6 slots
  public static int MIN_SEED_LOOKAHEAD = 1; // 2^0 epochs (6.4 minutes)
  public static int ACTIVATION_EXIT_DELAY = 4; // 2^2 epochs (25.6 minutes)
  public static int EPOCHS_PER_ETH1_VOTING_PERIOD = 16; // 2^4 epochs (~1.7 hours)
  public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY = 256; // 2^8 epochs (~27 hours)

  // Initial values
  public static int GENESIS_FORK_VERSION = 0;
  public static long GENESIS_SLOT = 0; // 2^32
  public static long GENESIS_EPOCH = slot_to_epoch(GENESIS_SLOT);
  public static long GENESIS_START_SHARD = 0;
  public static long FAR_FUTURE_EPOCH = -1L;
  public static Bytes32 ZERO_HASH = Bytes32.ZERO;
  public static BLSSignature EMPTY_SIGNATURE = BLSSignature.empty();
  public static Bytes BLS_WITHDRAWAL_PREFIX_BYTE = Bytes.EMPTY;

  // State list lengths
  public static int LATEST_BLOCK_ROOTS_LENGTH = 8192; // 2^13 slots (~13 hours)
  public static int LATEST_RANDAO_MIXES_LENGTH = 8192; // 2^13 epochs (~36 days)
  public static int LATEST_ACTIVE_INDEX_ROOTS_LENGTH = 8192; // 2^13 epochs (~36 days)
  public static int LATEST_SLASHED_EXIT_LENGTH = 8192; // 2^13 epochs (~36 days)

  // Reward and penalty quotients
  public static int BASE_REWARD_QUOTIENT = 32; // 2^5
  public static int WHISTLEBLOWER_REWARD_QUOTIENT = 512; // 2^9
  public static int ATTESTATION_INCLUSION_REWARD_QUOTIENT = 8; // 2^3
  public static int INACTIVITY_PENALTY_QUOTIENT = 16777216; // 2^24
  public static int MIN_PENALTY_QUOTIENT = 32; // 2^5

  // Max transactions per block
  public static int MAX_PROPOSER_SLASHINGS = 16; // 2^4
  public static int MAX_ATTESTER_SLASHINGS = 1; // 2^0
  public static int MAX_ATTESTATIONS = 128; // 2^7
  public static int MAX_DEPOSITS = 16; // 2^4
  public static int MAX_VOLUNTARY_EXITS = 16; // 2^4
  public static int MAX_TRANSFERS = 16; // 2^4

  // Signature domains
  public static int DOMAIN_DEPOSIT = 0;
  public static int DOMAIN_ATTESTATION = 1;
  public static int DOMAIN_PROPOSAL = 2;
  public static int DOMAIN_EXIT = 3;
  public static int DOMAIN_RANDAO = 4;
  public static int DOMAIN_TRANSFER = 5;

  // Artemis specific
  public static String SIM_DEPOSIT_VALUE = "1000000000000000000";
  public static int DEPOSIT_DATA_SIZE = 512; //

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
    BEACON_CHAIN_SHARD_NUMBER =
        config.getBeaconChainShardNumber() != -1L
            ? config.getBeaconChainShardNumber()
            : BEACON_CHAIN_SHARD_NUMBER; // 2^64 - 1
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
        config.getFarFutureEpoch() != -1L ? config.getFarFutureEpoch() : FAR_FUTURE_EPOCH;
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
    LATEST_BLOCK_ROOTS_LENGTH =
        config.getLatestBlockRootsLength() != Integer.MIN_VALUE
            ? config.getLatestBlockRootsLength()
            : LATEST_BLOCK_ROOTS_LENGTH; // 2^13 slots (~13 hours)
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
    DOMAIN_DEPOSIT =
        config.getDomainDeposit() != Integer.MIN_VALUE ? config.getDomainDeposit() : DOMAIN_DEPOSIT;
    DOMAIN_ATTESTATION =
        config.getDomainAttestation() != Integer.MIN_VALUE
            ? config.getDomainAttestation()
            : DOMAIN_ATTESTATION;
    DOMAIN_PROPOSAL =
        config.getDomainProposal() != Integer.MIN_VALUE
            ? config.getDomainProposal()
            : DOMAIN_PROPOSAL;
    DOMAIN_EXIT =
        config.getDomainExit() != Integer.MIN_VALUE ? config.getDomainExit() : DOMAIN_EXIT;
    DOMAIN_RANDAO =
        config.getDomainRandao() != Integer.MIN_VALUE ? config.getDomainRandao() : DOMAIN_RANDAO;
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

  public static long slot_to_epoch(long slot) {
    return slot / SLOTS_PER_EPOCH;
  }
}
