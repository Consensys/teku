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

public final class Constants {
  // The constants below are correct as of spec v0.4.0

  // Test Values
  /*public static final int SHARD_COUNT = 4;
  public static final int TARGET_COMMITTEE_SIZE = 2;
  public static final int MIN_ATTESTATION_INCLUSION_DELAY = 1;
  public static final int SLOTS_PER_EPOCH = 64;
  public static final int LATEST_BLOCK_ROOTS_LENGTH = 8192;
  public static final int LATEST_RANDAO_MIXES_LENGTH = 8192;
  public static final int LATEST_INDEX_ROOTS_LENGTH = 8192;
  public static final int LATEST_SLASHED_EXIT_LENGTH = 8192;*/

  // Misc
  public static final int SHARD_COUNT = 1024; // 2^10 shards
  public static final int TARGET_COMMITTEE_SIZE = 128; // 2^7 validators
  public static final int MAX_BALANCE_CHURN_QUOTIENT = 32; // 2^5
  public static final UnsignedLong BEACON_CHAIN_SHARD_NUMBER = UnsignedLong.MAX_VALUE; // 2^64 - 1
  public static final int MAX_INDICES_PER_SLASHABLE_VOTE = 4096; // 2^12 votes
  public static final int MAX_EXIT_DEQUEUES_PER_EPOCH = 4; // 2^2 withdrawals
  public static final int SHUFFLE_ROUND_COUNT = 90;

  // Deposit contract
  public static final String DEPOSIT_CONTRACT_ADDRESS = "0x0"; // This is TBD in the spec.
  public static final int DEPOSIT_CONTRACT_TREE_DEPTH = 32; // 2^5

  // Gwei values
  public static final long MIN_DEPOSIT_AMOUNT = 1000000000L; // 2^0 * 1E9 Gwei
  public static final long MAX_DEPOSIT_AMOUNT = 32000000000L; // 2^5 * 1E9 Gwei
  public static final long FORK_CHOICE_BALANCE_INCREMENT = 1000000000L; // 2^0 * 1E9 Gwei
  public static final long EJECTION_BALANCE = 16000000000L; // 2^4 * 1E9 Gwei

  // Initial values
  public static int GENESIS_FORK_VERSION = 0;
  public static final long GENESIS_SLOT = 4294967296L; // 2^32
  public static final long GENESIS_EPOCH = slot_to_epoch(GENESIS_SLOT);
  public static final long GENESIS_START_SHARD = 0;
  public static UnsignedLong FAR_FUTURE_EPOCH = UnsignedLong.MAX_VALUE;
  public static Bytes32 ZERO_HASH = Bytes32.ZERO; // TODO Verify this.
  public static final BLSSignature EMPTY_SIGNATURE = BLSSignature.empty();
  public static Bytes BLS_WITHDRAWAL_PREFIX_BYTE = Bytes.EMPTY; // TODO Verify this.

  // Time parameters
  public static final int SECONDS_PER_SLOT = 6; // 6 seconds
  public static final int MIN_ATTESTATION_INCLUSION_DELAY = 4; // 2^2 slots
  public static final int SLOTS_PER_EPOCH = 64; // 2^6 slots
  public static final int MIN_SEED_LOOKAHEAD = 1; // 2^0 epochs (6.4 minutes)
  public static final int ACTIVATION_EXIT_DELAY = 4; // 2^2 epochs (25.6 minutes)
  public static final int EPOCHS_PER_ETH1_VOTING_PERIOD = 16; // 2^4 epochs (~1.7 hours)
  public static final int MIN_VALIDATOR_WITHDRAWABILITY_DELAY = 256; // 2^8 epochs (~27 hours)

  // State list lengths
  public static final int LATEST_BLOCK_ROOTS_LENGTH = 8192; // 2^13 slots (~13 hours)
  public static final int LATEST_RANDAO_MIXES_LENGTH = 8192; // 2^13 epochs (~36 days)
  public static final int LATEST_INDEX_ROOTS_LENGTH = 8192; // 2^13 epochs (~36 days)
  public static final int LATEST_SLASHED_EXIT_LENGTH = 8192; // 2^13 epochs (~36 days)

  // Reward and penalty quotients
  public static final int BASE_REWARD_QUOTIENT = 32; // 2^5
  public static final int WHISTLEBLOWER_REWARD_QUOTIENT = 512; // 2^9
  public static final int ATTESTATION_INCLUSION_REWARD_QUOTIENT = 8; // 2^3
  public static final int INACTIVITY_PENALTY_QUOTIENT = 16777216; // 2^24
  public static final int MIN_PENALTY_QUOTIENT = 32; // 2^5

  // Max transactions per block
  public static final int MAX_PROPOSER_SLASHINGS = 16; // 2^4
  public static final int MAX_ATTESTER_SLASHINGS = 1; // 2^0
  public static final int MAX_ATTESTATIONS = 128; // 2^7
  public static final int MAX_DEPOSITS = 16; // 2^4
  public static final int MAX_VOLUNTARY_EXITS = 16; // 2^4
  public static final int MAX_TRANSFERS = 16; // 2^4

  // Signature domains
  public static final int DOMAIN_DEPOSIT = 0;
  public static final int DOMAIN_ATTESTATION = 1;
  public static final int DOMAIN_PROPOSAL = 2;
  public static final int DOMAIN_EXIT = 3;
  public static final int DOMAIN_RANDAO = 4;
  public static final int DOMAIN_TRANSFER = 5;

  // Artemis specific
  public static final String SIM_DEPOSIT_VALUE = "1000000000000000000";
  public static final int DEPOSIT_DATA_SIZE = 512; //

  private static long slot_to_epoch(long slot) {
    return slot / Constants.SLOTS_PER_EPOCH;
  }
}
