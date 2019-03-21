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
import java.util.HashMap;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class Constants {
  // The constants below are correct as of spec v0.1
  public HashMap<String, Object> config;

  // Misc
  public int SHARD_COUNT;
  public int TARGET_COMMITTEE_SIZE;
  public long EJECTION_BALANCE;
  public int MAX_BALANCE_CHURN_QUOTIENT;
  public UnsignedLong BEACON_CHAIN_SHARD_NUMBER;
  public int MAX_INDICES_PER_SLASHABLE_VOTE;
  public int MAX_WITHDRAWALS_PER_EPOCH;

  // Deposit contract
  public String DEPOSIT_CONTRACT_ADDRESS;
  public int DEPOSIT_CONTRACT_TREE_DEPTH;
  public long MIN_DEPOSIT_AMOUNT;
  public long MAX_DEPOSIT_AMOUNT;

  // Initial values
  public int GENESIS_FORK_VERSION;
  public long GENESIS_SLOT;
  public long GENESIS_EPOCH;
  public long GENESIS_START_SHARD;
  public UnsignedLong FAR_FUTURE_EPOCH;
  public Bytes32 ZERO_HASH;
  public BLSSignature EMPTY_SIGNATURE;
  public Bytes BLS_WITHDRAWAL_PREFIX_BYTE;

  // Time parameters
  public int SLOT_DURATION;
  public int MIN_ATTESTATION_INCLUSION_DELAY;
  public int EPOCH_LENGTH;
  public int SEED_LOOKAHEAD;
  public int ENTRY_EXIT_DELAY;
  public int ETH1_DATA_VOTING_PERIOD;
  public int MIN_VALIDATOR_WITHDRAWAL_EPOCHS;

  // State list lengths
  public int LATEST_BLOCK_ROOTS_LENGTH;
  public int LATEST_RANDAO_MIXES_LENGTH;
  public int LATEST_INDEX_ROOTS_LENGTH;
  public int LATEST_PENALIZED_EXIT_LENGTH;

  // Reward and penalty quotients
  public int BASE_REWARD_QUOTIENT;
  public int WHISTLEBLOWER_REWARD_QUOTIENT;
  public int INCLUDER_REWARD_QUOTIENT;
  public int INACTIVITY_PENALTY_QUOTIENT;

  // Status flags
  public int INITIATED_EXIT;
  public int WITHDRAWABLE;

  // Max transactions per block
  public int MAX_PROPOSER_SLASHINGS;
  public int MAX_ATTESTER_SLASHINGS;
  public int MAX_ATTESTATIONS;
  public int MAX_DEPOSITS;
  public int MAX_EXITS;

  // Signature domains
  public int DOMAIN_DEPOSIT;
  public int DOMAIN_ATTESTATION;
  public int DOMAIN_PROPOSAL;
  public int DOMAIN_EXIT;
  public int DOMAIN_RANDAO;

  // Artemis specific
  public String SIM_DEPOSIT_VALUE;
  public int DEPOSIT_DATA_SIZE;

  public Constants(ArtemisConfiguration config) {

    // Misc
    this.SHARD_COUNT =
        config.getShardCount() != Integer.MIN_VALUE ? config.getShardCount() : 1024; // 2^10 shards
    this.TARGET_COMMITTEE_SIZE =
        config.getTargetCommitteeSize() != Integer.MIN_VALUE
            ? config.getTargetCommitteeSize()
            : 128; // 2^7 validators
    this.EJECTION_BALANCE =
        config.getEjectionBalance() != Long.MIN_VALUE
            ? config.getEjectionBalance()
            : 16000000000L; // 2^4 * 1E9 Gwei
    this.MAX_BALANCE_CHURN_QUOTIENT =
        config.getMaxBalanceChurnQuotient() != Integer.MIN_VALUE
            ? config.getMaxBalanceChurnQuotient()
            : 32; // 2^5
    this.BEACON_CHAIN_SHARD_NUMBER =
        !config.getBeaconChainShardNumber().equals("")
            ? (UnsignedLong) config.getBeaconChainShardNumber()
            : UnsignedLong.MAX_VALUE; // 2^64 - 1
    this.MAX_INDICES_PER_SLASHABLE_VOTE =
        config.getMaxIndicesPerSlashableVote() != Integer.MIN_VALUE
            ? config.getMaxIndicesPerSlashableVote()
            : 4096; // 2^12 votes
    this.MAX_WITHDRAWALS_PER_EPOCH =
        config.getMaxWithdrawalsPerEpoch() != Integer.MIN_VALUE
            ? config.getMaxWithdrawalsPerEpoch()
            : 4; // 2^2 withdrawals

    // Deposit contract
    this.DEPOSIT_CONTRACT_ADDRESS =
        !config.getDepositContractAddress().equals("")
            ? config.getDepositContractAddress()
            : "0x0"; // This is TBD in the spec.
    this.DEPOSIT_CONTRACT_TREE_DEPTH =
        config.getDepositContractTreeDepth() != Integer.MIN_VALUE
            ? config.getDepositContractTreeDepth()
            : 32; // 2^5
    this.MIN_DEPOSIT_AMOUNT =
        config.getMinDepositAmount() != Long.MIN_VALUE
            ? config.getMinDepositAmount()
            : 1000000000L; // 2^0 * 1E9 Gwei
    this.MAX_DEPOSIT_AMOUNT =
        config.getMaxDepositAmount() != Long.MIN_VALUE
            ? config.getMaxDepositAmount()
            : 32000000000L; // 2^5 * 1E9 Gwei

    // Initial values
    this.GENESIS_FORK_VERSION =
        config.getGenesisForkVersion() != Integer.MIN_VALUE ? config.getGenesisForkVersion() : 0;
    this.GENESIS_SLOT =
        config.getGenesisSlot() != Long.MIN_VALUE ? config.getGenesisSlot() : 4294967296L; // 2^32
    this.GENESIS_EPOCH =
        config.getGenesisEpoch() != Long.MIN_VALUE
            ? config.getGenesisEpoch()
            : slot_to_epoch(GENESIS_SLOT);
    this.GENESIS_START_SHARD =
        config.getGenesisStartShard() != Integer.MIN_VALUE ? config.getGenesisStartShard() : 0;
    this.FAR_FUTURE_EPOCH =
        !config.getFarFutureEpoch().equals("")
            ? (UnsignedLong) config.getFarFutureEpoch()
            : UnsignedLong.MAX_VALUE;
    this.ZERO_HASH =
        !config.getZeroHash().equals("")
            ? (Bytes32) config.getZeroHash()
            : Bytes32.ZERO; // TODO Verify this.
    this.EMPTY_SIGNATURE =
        !config.getEmptySignature().equals("")
            ? (BLSSignature) config.getEmptySignature()
            : BLSSignature.empty();
    this.BLS_WITHDRAWAL_PREFIX_BYTE =
        !config.getBlsWithdrawalPrefixByte().equals("")
            ? (Bytes) config.getBlsWithdrawalPrefixByte()
            : Bytes.EMPTY; // TODO Verify this.

    // Time parameters
    this.SLOT_DURATION =
        config.getSlotDuration() != Integer.MIN_VALUE ? config.getSlotDuration() : 6; // 6 seconds
    this.MIN_ATTESTATION_INCLUSION_DELAY =
        config.getMinAttestationInclusionDelay() != Integer.MIN_VALUE
            ? config.getMinAttestationInclusionDelay()
            : 4; // 2^2 slots
    this.EPOCH_LENGTH =
        config.getEpochLength() != Integer.MIN_VALUE ? config.getEpochLength() : 64; // 2^6 slots
    this.SEED_LOOKAHEAD =
        config.getSeedLookahead() != Integer.MIN_VALUE
            ? config.getSeedLookahead()
            : 1; // 2^0 epochs (6.4 minutes)
    this.ENTRY_EXIT_DELAY =
        config.getEntryExitDelay() != Integer.MIN_VALUE
            ? config.getEntryExitDelay()
            : 4; // 2^2 epochs (25.6 minutes)
    this.ETH1_DATA_VOTING_PERIOD =
        config.getEth1DataVotingPeriod() != Integer.MIN_VALUE
            ? config.getEth1DataVotingPeriod()
            : 16; // 2^4 epochs (~1.7 hours)
    this.MIN_VALIDATOR_WITHDRAWAL_EPOCHS =
        config.getMinValidatorWithdrawalEpochs() != Integer.MIN_VALUE
            ? config.getMinValidatorWithdrawalEpochs()
            : 256; // 2^8 epochs (~27 hours)

    // State list lengths
    this.LATEST_BLOCK_ROOTS_LENGTH =
        config.getLatestBlockRootsLength() != Integer.MIN_VALUE
            ? config.getLatestBlockRootsLength()
            : 8192; // 2^13 slots (~13 hours)
    this.LATEST_RANDAO_MIXES_LENGTH =
        config.getLatestRandaoMixesLength() != Integer.MIN_VALUE
            ? config.getLatestRandaoMixesLength()
            : 8192; // 2^13 epochs (~36 days)
    this.LATEST_INDEX_ROOTS_LENGTH =
        config.getLatestIndexRootsLength() != Integer.MIN_VALUE
            ? config.getLatestIndexRootsLength()
            : 8192; // 2^13 epochs (~36 days)
    this.LATEST_PENALIZED_EXIT_LENGTH =
        config.getLatestPenalizedExitLength() != Integer.MIN_VALUE
            ? config.getLatestPenalizedExitLength()
            : 8192; // 2^13 epochs (~36 days)

    // Reward and penalty quotients
    this.BASE_REWARD_QUOTIENT =
        config.getBaseRewardQuotient() != Integer.MIN_VALUE
            ? config.getBaseRewardQuotient()
            : 32; // 2^5
    this.WHISTLEBLOWER_REWARD_QUOTIENT =
        config.getWhistleblowerRewardQuotient() != Integer.MIN_VALUE
            ? config.getWhistleblowerRewardQuotient()
            : 512; // 2^9
    this.INCLUDER_REWARD_QUOTIENT =
        config.getIncluderRewardQuotient() != Integer.MIN_VALUE
            ? config.getIncluderRewardQuotient()
            : 8; // 2^3
    this.INACTIVITY_PENALTY_QUOTIENT =
        config.getInactivityPenaltyQuotient() != Integer.MIN_VALUE
            ? config.getInactivityPenaltyQuotient()
            : 16777216; // 2^24

    // Status flags
    this.INITIATED_EXIT =
        config.getInitiatedExit() != Integer.MIN_VALUE ? config.getInitiatedExit() : 1;
    this.WITHDRAWABLE =
        config.getWithdrawable() != Integer.MIN_VALUE ? config.getWithdrawable() : 2;

    // Max transactions per block
    this.MAX_PROPOSER_SLASHINGS =
        config.getMaxProposerSlashings() != Integer.MIN_VALUE
            ? config.getMaxProposerSlashings()
            : 16; // 2^4
    this.MAX_ATTESTER_SLASHINGS =
        config.getMaxAttesterSlashings() != Integer.MIN_VALUE
            ? config.getMaxAttesterSlashings()
            : 1; // 2^0
    this.MAX_ATTESTATIONS =
        config.getMaxAttestations() != Integer.MIN_VALUE ? config.getMaxAttestations() : 128; // 2^7
    this.MAX_DEPOSITS =
        config.getMaxDeposits() != Integer.MIN_VALUE ? config.getMaxDeposits() : 16; // 2^4
    this.MAX_EXITS = config.getMaxExits() != Integer.MIN_VALUE ? config.getMaxExits() : 16; // 2^4

    // Signature domains
    this.DOMAIN_DEPOSIT =
        config.getDomainDeposit() != Integer.MIN_VALUE ? config.getDomainDeposit() : 0;
    this.DOMAIN_ATTESTATION =
        config.getDomainAttestation() != Integer.MIN_VALUE ? config.getDomainAttestation() : 1;
    this.DOMAIN_PROPOSAL =
        config.getDomainProposal() != Integer.MIN_VALUE ? config.getDomainProposal() : 2;
    this.DOMAIN_EXIT = config.getDomainExit() != Integer.MIN_VALUE ? config.getDomainExit() : 3;
    this.DOMAIN_RANDAO =
        config.getDomainRandao() != Integer.MIN_VALUE ? config.getDomainRandao() : 4;

    // Artemis specific
    this.SIM_DEPOSIT_VALUE =
        !config.getSimDepositValue().equals("")
            ? config.getSimDepositValue()
            : "1000000000000000000";
    this.DEPOSIT_DATA_SIZE =
        config.getDepositDataSize() != Integer.MIN_VALUE ? config.getDepositDataSize() : 512;
  }

  private long slot_to_epoch(long slot) {
    return slot / (long) config.get("EPOCH_LENGTH");
  }
}
