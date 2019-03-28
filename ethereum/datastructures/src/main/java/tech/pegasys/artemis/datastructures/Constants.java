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
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class Constants {
  // The constants below are correct as of spec v0.4

  // Misc
  public static int SHARD_COUNT;
  public static int TARGET_COMMITTEE_SIZE;
  public static int MAX_BALANCE_CHURN_QUOTIENT;
  public static UnsignedLong BEACON_CHAIN_SHARD_NUMBER;
  public static int MAX_INDICES_PER_SLASHABLE_VOTE;
  public static int MAX_EXIT_DEQUEUES_PER_EPOCH;
  public static int SHUFFLE_ROUND_COUNT;

  // Deposit contract
  public static String DEPOSIT_CONTRACT_ADDRESS;
  public static int DEPOSIT_CONTRACT_TREE_DEPTH;

  // Gwei values
  public static long MIN_DEPOSIT_AMOUNT;
  public static long MAX_DEPOSIT_AMOUNT;
  public static long FORK_CHOICE_BALANCE_INCREMENT;
  public static long EJECTION_BALANCE;

  // Initial values
  public static int GENESIS_FORK_VERSION;
  public static long GENESIS_SLOT;
  public static long GENESIS_EPOCH;
  public static long GENESIS_START_SHARD;
  public static UnsignedLong FAR_FUTURE_EPOCH;
  public static Bytes32 ZERO_HASH;
  public static BLSSignature EMPTY_SIGNATURE;
  public static String BLS_WITHDRAWAL_PREFIX_BYTE;

  // Time parameters
  public static int SECONDS_PER_SLOT;
  public static int MIN_ATTESTATION_INCLUSION_DELAY;
  public static int SLOTS_PER_EPOCH;
  public static int MIN_SEED_LOOKAHEAD;
  public static int ACTIVATION_EXIT_DELAY;
  public static int EPOCHS_PER_ETH1_VOTING_PERIOD;
  public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY;

  // State list lengths
  public static int LATEST_BLOCK_ROOTS_LENGTH;
  public static int LATEST_RANDAO_MIXES_LENGTH;
  public static int LATEST_ACTIVE_INDEX_ROOTS_LENGTH;
  public static int LATEST_SLASHED_EXIT_LENGTH;

  // Reward and penalty quotients
  public static int BASE_REWARD_QUOTIENT;
  public static int WHISTLEBLOWER_REWARD_QUOTIENT;
  public static int ATTESTATION_INCLUSION_REWARD_QUOTIENT;
  public static int INACTIVITY_PENALTY_QUOTIENT;
  public static int MIN_PENALTY_QUOTIENT;

  // Max transactions per block
  public static int MAX_PROPOSER_SLASHINGS;
  public static int MAX_ATTESTER_SLASHINGS;
  public static int MAX_ATTESTATIONS;
  public static int MAX_DEPOSITS;
  public static int MAX_VOLUNTARY_EXITS;
  public static int MAX_TRANSFERS;

  // Signature domains
  public static int DOMAIN_DEPOSIT;
  public static int DOMAIN_ATTESTATION;
  public static int DOMAIN_PROPOSAL;
  public static int DOMAIN_EXIT;
  public static int DOMAIN_RANDAO;
  public static int DOMAIN_TRANSFER;

  // Artemis specific
  public static String SIM_DEPOSIT_VALUE;
  public static int DEPOSIT_DATA_SIZE;

  public static void init(ArtemisConfiguration config) {
    // Misc
    SHARD_COUNT =
        config.getShardCount() != Integer.MIN_VALUE ? config.getShardCount() : 1024; // 2^10 shards
    TARGET_COMMITTEE_SIZE =
        config.getTargetCommitteeSize() != Integer.MIN_VALUE
            ? config.getTargetCommitteeSize()
            : 128; // 2^7 validators
    MAX_BALANCE_CHURN_QUOTIENT =
        config.getMaxBalanceChurnQuotient() != Integer.MIN_VALUE
            ? config.getMaxBalanceChurnQuotient()
            : 32; // 2^5
    BEACON_CHAIN_SHARD_NUMBER =
        !config.getBeaconChainShardNumber().equals(UnsignedLong.MAX_VALUE)
            ? (UnsignedLong) config.getBeaconChainShardNumber()
            : UnsignedLong.MAX_VALUE; // 2^64 - 1
    MAX_INDICES_PER_SLASHABLE_VOTE =
        config.getMaxIndicesPerSlashableVote() != Integer.MIN_VALUE
            ? config.getMaxIndicesPerSlashableVote()
            : 4096; // 2^12 votes
    MAX_EXIT_DEQUEUES_PER_EPOCH =
        config.getMaxExitDequeuesPerEpoch() != Integer.MIN_VALUE
            ? config.getMaxExitDequeuesPerEpoch()
            : 4; // 2^2 withdrawals
    SHUFFLE_ROUND_COUNT =
        config.getShuffleRoundCount() != Integer.MIN_VALUE ? config.getShuffleRoundCount() : 90;

    // Deposit contract
    DEPOSIT_CONTRACT_ADDRESS =
        !config.getDepositContractAddress().equals("")
            ? config.getDepositContractAddress()
            : "0x0"; // This is TBD in the spec.
    DEPOSIT_CONTRACT_TREE_DEPTH =
        config.getDepositContractTreeDepth() != Integer.MIN_VALUE
            ? config.getDepositContractTreeDepth()
            : 32; // 2^5

    // Gwei values
    MIN_DEPOSIT_AMOUNT =
        config.getMinDepositAmount() != Long.MIN_VALUE
            ? config.getMinDepositAmount()
            : 1000000000L; // 2^0 * 1E9 Gwei
    MAX_DEPOSIT_AMOUNT =
        config.getMaxDepositAmount() != Long.MIN_VALUE
            ? config.getMaxDepositAmount()
            : 32000000000L; // 2^5 * 1E9 Gwei
    FORK_CHOICE_BALANCE_INCREMENT =
        config.getForkChoiceBalanceIncrement() != Long.MIN_VALUE
            ? config.getForkChoiceBalanceIncrement()
            : 1000000000L;
    EJECTION_BALANCE =
        config.getEjectionBalance() != Long.MIN_VALUE
            ? config.getEjectionBalance()
            : 16000000000L; // 2^4 * 1E9 Gwei

    // Time parameters
    SECONDS_PER_SLOT =
        config.getSecondsPerSlot() != Integer.MIN_VALUE
            ? config.getSecondsPerSlot()
            : 6; // 6 seconds
    MIN_ATTESTATION_INCLUSION_DELAY =
        config.getMinAttestationInclusionDelay() != Integer.MIN_VALUE
            ? config.getMinAttestationInclusionDelay()
            : 4; // 2^2 slots
    SLOTS_PER_EPOCH =
        config.getSlotsPerEpoch() != Integer.MIN_VALUE
            ? config.getSlotsPerEpoch()
            : 64; // 2^6 slots
    MIN_SEED_LOOKAHEAD =
        config.getMinSeedLookahead() != Integer.MIN_VALUE
            ? config.getMinSeedLookahead()
            : 1; // 2^0 epochs (6.4 minutes)
    ACTIVATION_EXIT_DELAY =
        config.getActivationExitDelay() != Integer.MIN_VALUE
            ? config.getActivationExitDelay()
            : 4; // 2^2 epochs (25.6 minutes)
    EPOCHS_PER_ETH1_VOTING_PERIOD =
        config.getEpochsPerEth1VotingPeriod() != Integer.MIN_VALUE
            ? config.getEpochsPerEth1VotingPeriod()
            : 16; // 2^4 epochs (~1.7 hours)
    MIN_VALIDATOR_WITHDRAWABILITY_DELAY =
        config.getMinValidatorWithdrawabilityDelay() != Integer.MIN_VALUE
            ? config.getMinValidatorWithdrawabilityDelay()
            : 256; // 2^8 epochs (~27 hours)

    // Initial values
    GENESIS_FORK_VERSION =
        config.getGenesisForkVersion() != Integer.MIN_VALUE ? config.getGenesisForkVersion() : 0;
    GENESIS_SLOT =
        config.getGenesisSlot() != Long.MIN_VALUE ? config.getGenesisSlot() : 4294967296L; // 2^32
    GENESIS_EPOCH =
        config.getGenesisEpoch() != Long.MIN_VALUE
            ? config.getGenesisEpoch()
            : slot_to_epoch(GENESIS_SLOT);
    GENESIS_START_SHARD =
        config.getGenesisStartShard() != Integer.MIN_VALUE ? config.getGenesisStartShard() : 0;
    FAR_FUTURE_EPOCH =
        !config.getFarFutureEpoch().equals(UnsignedLong.MAX_VALUE)
            ? (UnsignedLong) config.getFarFutureEpoch()
            : UnsignedLong.MAX_VALUE;
    Bytes32 ZERO_HASH =
        !config.getZeroHash().equals(Bytes32.ZERO)
            ? (Bytes32) config.getZeroHash()
            : Bytes32.ZERO; // TODO Verify
    BLSSignature EMPTY_SIGNATURE =
        !config.getEmptySignature().equals(BLSSignature.empty())
            ? (BLSSignature) config.getEmptySignature()
            : BLSSignature.empty();
    BLS_WITHDRAWAL_PREFIX_BYTE =
        !config.getBlsWithdrawalPrefixByte().equals("")
            ? config.getBlsWithdrawalPrefixByte()
            : "0x00"; // TODO Verify

    // State list lengths
    LATEST_BLOCK_ROOTS_LENGTH =
        config.getLatestBlockRootsLength() != Integer.MIN_VALUE
            ? config.getLatestBlockRootsLength()
            : 8192; // 2^13 slots (~13 hours)
    LATEST_RANDAO_MIXES_LENGTH =
        config.getLatestRandaoMixesLength() != Integer.MIN_VALUE
            ? config.getLatestRandaoMixesLength()
            : 8192; // 2^13 epochs (~36 days)
    LATEST_ACTIVE_INDEX_ROOTS_LENGTH =
        config.getLatestActiveIndexRootsLength() != Integer.MIN_VALUE
            ? config.getLatestActiveIndexRootsLength()
            : 8192; // 2^13 epochs (~36 days)
    LATEST_SLASHED_EXIT_LENGTH =
        config.getLatestSlashedExitLength() != Integer.MIN_VALUE
            ? config.getLatestSlashedExitLength()
            : 8192; // 2^13 epochs (~36 days)

    // Reward and penalty quotients
    BASE_REWARD_QUOTIENT =
        config.getBaseRewardQuotient() != Integer.MIN_VALUE
            ? config.getBaseRewardQuotient()
            : 32; // 2^5
    WHISTLEBLOWER_REWARD_QUOTIENT =
        config.getWhistleblowerRewardQuotient() != Integer.MIN_VALUE
            ? config.getWhistleblowerRewardQuotient()
            : 512; // 2^9
    ATTESTATION_INCLUSION_REWARD_QUOTIENT =
        config.getAttestationInclusionRewardQuotient() != Integer.MIN_VALUE
            ? config.getAttestationInclusionRewardQuotient()
            : 8; // 2^3
    INACTIVITY_PENALTY_QUOTIENT =
        config.getInactivityPenaltyQuotient() != Integer.MIN_VALUE
            ? config.getInactivityPenaltyQuotient()
            : 16777216; // 2^24
    MIN_PENALTY_QUOTIENT =
        config.getMinPenaltyQuotient() != Integer.MIN_VALUE
            ? config.getMinPenaltyQuotient()
            : 32; // 2^5

    // Max transactions per block
    MAX_PROPOSER_SLASHINGS =
        config.getMaxProposerSlashings() != Integer.MIN_VALUE
            ? config.getMaxProposerSlashings()
            : 16; // 2^4
    MAX_ATTESTER_SLASHINGS =
        config.getMaxAttesterSlashings() != Integer.MIN_VALUE
            ? config.getMaxAttesterSlashings()
            : 1; // 2^0
    MAX_ATTESTATIONS =
        config.getMaxAttestations() != Integer.MIN_VALUE ? config.getMaxAttestations() : 128; // 2^7
    MAX_DEPOSITS =
        config.getMaxDeposits() != Integer.MIN_VALUE ? config.getMaxDeposits() : 16; // 2^4
    MAX_VOLUNTARY_EXITS =
        config.getMaxVoluntaryExits() != Integer.MIN_VALUE
            ? config.getMaxVoluntaryExits()
            : 16; // 2^4
    MAX_TRANSFERS =
        config.getMaxTransfers() != Integer.MIN_VALUE ? config.getMaxTransfers() : 16; // 2^4

    // Signature domains
    DOMAIN_DEPOSIT = config.getDomainDeposit() != Integer.MIN_VALUE ? config.getDomainDeposit() : 0;
    DOMAIN_ATTESTATION =
        config.getDomainAttestation() != Integer.MIN_VALUE ? config.getDomainAttestation() : 1;
    DOMAIN_PROPOSAL =
        config.getDomainProposal() != Integer.MIN_VALUE ? config.getDomainProposal() : 2;
    DOMAIN_EXIT = config.getDomainExit() != Integer.MIN_VALUE ? config.getDomainExit() : 3;
    DOMAIN_RANDAO = config.getDomainRandao() != Integer.MIN_VALUE ? config.getDomainRandao() : 4;
    DOMAIN_TRANSFER =
        config.getDomainTransfer() != Integer.MIN_VALUE ? config.getDomainTransfer() : 5;

    // Artemis specific
    SIM_DEPOSIT_VALUE =
        !config.getSimDepositValue().equals("")
            ? config.getSimDepositValue()
            : "1000000000000000000";
    DEPOSIT_DATA_SIZE =
        config.getDepositDataSize() != Integer.MIN_VALUE ? config.getDepositDataSize() : 512;
  }

  public static long slot_to_epoch(long slot) {
    return slot / SLOTS_PER_EPOCH;
  }
}
