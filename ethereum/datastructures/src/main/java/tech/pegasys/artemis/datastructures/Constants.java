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
import java.util.Arrays;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;

public final class Constants {
  // The constants below are correct as of spec v0.1

  // Misc
  public static final int SHARD_COUNT = 1024; // 2^10 shards
  public static final int TARGET_COMMITTEE_SIZE = 128; // 2^7 validators
  public static final long EJECTION_BALANCE = 16000000000L; // 2^4 * 1e9 Gwei
  public static final int MAX_BALANCE_CHURN_QUOTIENT = 32; //
  public static final UnsignedLong BEACON_CHAIN_SHARD_NUMBER = UnsignedLong.MAX_VALUE; // 2^64 - 1
  public static final int MAX_INDICES_PER_SLASHABLE_VOTE = 4096; // 2^12 votes
  public static final int MAX_WITHDRAWALS_PER_EPOCH = 4; // withdrawals

  // Deposit contract
  public static final String DEPOSIT_CONTRACT_ADDRESS = "0x0";
  public static final int DEPOSIT_CONTRACT_TREE_DEPTH = 32; // 2^5
  public static final long MIN_DEPOSIT_AMOUNT = 1000000000L; // Gwei
  public static final long MAX_DEPOSIT_AMOUNT = 32000000000L; // Gwei

  // Initial values
  public static int GENESIS_FORK_VERSION = 0; //
  // TODO v.0.1 has 2^19; this will be updated to 2^63 in a future version.
  // TODO continued: Postpone change to UnsignedLong until that time
  // public static UnsignedLong GENESIS_SLOT = UnsignedLong.valueOf("9223372036854775808"); // 2^63
  // public static final UnsignedLong GENESIS_EPOCH = slot_to_epoch(GENESIS_SLOT);
  public static long GENESIS_SLOT = 524288L; // 2^19
  public static final long GENESIS_EPOCH = GENESIS_SLOT / Constants.EPOCH_LENGTH;
  public static int GENESIS_START_SHARD = 0; //
  public static UnsignedLong FAR_FUTURE_EPOCH = UnsignedLong.MAX_VALUE; //
  public static Bytes32 ZERO_HASH = Bytes32.ZERO; //
  public static final List<Bytes48> EMPTY_SIGNATURE =
      Arrays.asList(Bytes48.ZERO, Bytes48.ZERO); // Bytes96
  public static Bytes BLS_WITHDRAWAL_PREFIX_BYTE = Bytes.EMPTY;

  // Time parameters
  public static final int SLOT_DURATION = 6; // 6 seconds
  public static final int MIN_ATTESTATION_INCLUSION_DELAY = 4; // 2^2 slots
  public static final int EPOCH_LENGTH = 64; // 2^6 slots
  public static final int SEED_LOOKAHEAD = 1; // epochs 6.4 minutes
  public static final int ENTRY_EXIT_DELAY = 4; // 2^2 epochs 25.6 minutes
  public static final int ETH1_DATA_VOTING_PERIOD = 16; // 2^4 epochs ~1.7 hours
  public static final int MIN_VALIDATOR_WITHDRAWAL_EPOCHS = 256; // 2^8 epochs ~27 hours

  // State list lengths
  public static final int LATEST_BLOCK_ROOTS_LENGTH = 8192; // 2^13 slots ~13 hours
  public static final int LATEST_RANDAO_MIXES_LENGTH = 8192; // 2^13 epochs ~36 days
  public static final int LATEST_INDEX_ROOTS_LENGTH = 8192; // 2^13 epochs ~36 days
  public static final int LATEST_PENALIZED_EXIT_LENGTH = 8192; // 2^13 epochs ~36 days

  // Reward and penalty quotients
  public static final int BASE_REWARD_QUOTIENT = 32; // 2^5
  public static final int WHISTLEBLOWER_REWARD_QUOTIENT = 512; // 2^9
  public static final int INCLUDER_REWARD_QUOTIENT = 8; // 2^3
  public static final int INACTIVITY_PENALTY_QUOTIENT = 16777216; // 2^24

  // Status flags
  public static final int INITIATED_EXIT = 1; // 2^0
  public static final int WITHDRAWABLE = 2; // 2^1

  // Max operations per block
  public static final int MAX_PROPOSER_SLASHINGS = 16; // 2^4
  public static final int MAX_ATTESTER_SLASHINGS = 1; // 2^0
  public static final int MAX_ATTESTATIONS = 128; // 2^7
  public static final int MAX_DEPOSITS = 16; // 2^4
  public static final int MAX_EXITS = 16; // 2^4

  // Signature domains
  public static final int DOMAIN_DEPOSIT = 0; //
  public static final int DOMAIN_ATTESTATION = 1; //
  public static final int DOMAIN_PROPOSAL = 2; //
  public static final int DOMAIN_EXIT = 3; //
  public static final int MAX_DOMAIN_RANDAOEXITS = 4; //

  private static UnsignedLong slot_to_epoch(UnsignedLong slot) {
    return slot.dividedBy(UnsignedLong.valueOf(Constants.EPOCH_LENGTH));
  }
}
