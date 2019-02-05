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
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;

public final class Constants {

  // The constants below are correct as of spec dated 2018/12/5

  // Misc
  public static final int SHARD_COUNT = (int) Math.pow(2, 10); // 1,024 Shards
  public static final int TARGET_COMMITTEE_SIZE = (int) Math.pow(2, 7); // 128 validators
  public static final int EJECTION_BALANCE = (int) Math.pow(2, 4); // 16 Eth
  public static final int MAX_BALANCE_CHURN_QUOTIENT = (int) Math.pow(2, 5); // 32
  public static final int GWEI_PER_ETH = (int) Math.pow(10, 9); // 1,000,000,000 Wei
  public static final UnsignedLong BEACON_CHAIN_SHARD_NUMBER = UnsignedLong.MAX_VALUE;
  public static final String BLS_WITHDRAWAL_PREFIX_BYTE = "0x00";
  public static final int MAX_CASPER_VOTES = (int) Math.pow(2, 10); // 1,024 votes
  public static final int LATEST_BLOCK_ROOTS_LENGTH = (int) Math.pow(2, 13); // 8,192 block roots
  public static final int LATEST_RANDAO_MIXES_LENGTH = (int) Math.pow(2, 13); // 8,192 randao mixes
  public static final List<Bytes48> EMPTY_SIGNATURE = Arrays.asList(Bytes48.ZERO, Bytes48.ZERO);
  public static final int LATEST_PENALIZED_EXIT_LENGTH = (int) Math.pow(2, 13); // 8,192 epochs

  // Deposit contract
  //  static final Address DEPOSIT_CONTRACT_ADDRESS               =  Value is still TBD
  public static final int DEPOSIT_CONTRACT_TREE_DEPTH = (int) Math.pow(2, 5); // 32
  public static final int MIN_DEPOSIT = (int) Math.pow(2, 0); // 1 Eth
  public static final int MAX_DEPOSIT = (int) Math.pow(2, 5); // 32 Eth

  // Initial values
  public static final int INITIAL_FORK_VERSION = 0;
  public static final int INITIAL_SLOT_NUMBER = 0;
  public static final Bytes32 ZERO_HASH = Bytes32.ZERO;

  // Time parameters
  public static final int SLOT_DURATION = 6; // 6 seconds
  public static final int MIN_ATTESTATION_INCLUSION_DELAY = 4; // 4 slots
  public static final int EPOCH_LENGTH = 64; // slots
  public static final int SEED_LOOKAHEAD = 1;
  public static final int ENTRY_EXIT_DELAY = 4;
  public static final int ETH1_DATA_VOTING_PERIOD = 16; // epochs
  public static final int MIN_VALIDATOR_WITHDRAWAL_EPOCHS = 256; // epochs

  // Reward and penalty quotients
  public static final int BASE_REWARD_QUOTIENT = (int) Math.pow(2, 10); // 1,024
  public static final int WHISTLEBLOWER_REWARD_QUOTIENT = (int) Math.pow(2, 9); // 512
  public static final int INCLUDER_REWARD_QUOTIENT = (int) Math.pow(2, 3); // 8
  public static final int INACTIVITY_PENALTY_QUOTIENT = (int) Math.pow(2, 24); // 16,777,216

  // Status codes
  public static final int INITIATED_EXIT = 1;
  public static final int WITHDRAWABLE = 2;

  // Max operations per block
  public static final int MAX_PROPOSER_SLASHINGS = (int) Math.pow(2, 4); // 16
  public static final int MAX_CASPER_SLASHINGS = (int) Math.pow(2, 4); // 16
  public static final int MAX_ATTESTATIONS = (int) Math.pow(2, 7); // 128
  public static final int MAX_DEPOSITS = (int) Math.pow(2, 4); // 16
  public static final int MAX_EXITS = (int) Math.pow(2, 4); // 16

  // Validator registry delta flags
  public static final int ACTIVATION = 0;
  public static final int EXIT = 1;

  // Signature domains
  public static final UnsignedLong DOMAIN_DEPOSIT = UnsignedLong.valueOf(0);
  public static final UnsignedLong DOMAIN_ATTESTATION = UnsignedLong.valueOf(1);
  public static final UnsignedLong DOMAIN_PROPOSAL = UnsignedLong.valueOf(2);
  public static final UnsignedLong DOMAIN_EXIT = UnsignedLong.valueOf(3);
  public static final UnsignedLong DOMAIN_RANDAO = UnsignedLong.valueOf(4);
  public static final int MIN_VALIDATOR_WITHDRAWAL_TIME =
      (int) Math.pow(2, 14); // 16,384 slots ~27 hours
  public static final int MAX_WITHDRAWALS_PER_EPOCH = 4;

  public static final long GENESIS_SLOT = (long) Math.pow(2, 19);
  public static final long GENESIS_EPOCH = GENESIS_SLOT / Constants.EPOCH_LENGTH;

  public static String getConstantsAsString() {
    return "--Misc--"
        + "\nSHARD_COUNT: "
        + SHARD_COUNT
        + "\nTARGET_COMMITTEE_SIZE: "
        + TARGET_COMMITTEE_SIZE
        + "\nMIN_BALANCE: "
        + EJECTION_BALANCE
        + "\nMAX_BALANCE_CHURN_QUOTIENT: "
        + MAX_BALANCE_CHURN_QUOTIENT
        + "\nGWEI_PER_ETH: "
        + GWEI_PER_ETH
        + "\nBEACON_CHAIN_SHARD_NUMBER: "
        + BEACON_CHAIN_SHARD_NUMBER
        + "\nBLS_WITHDRAWAL_CREDENTIALS: "
        + BLS_WITHDRAWAL_PREFIX_BYTE
        + "\nMAX_CASPER_VOTES: "
        + MAX_CASPER_VOTES
        + "\nLATEST_BLOCK_ROOTS_LENGTH: "
        + LATEST_BLOCK_ROOTS_LENGTH
        + "\nLATEST_RANDAO_MIXES_LENGTH: "
        + LATEST_RANDAO_MIXES_LENGTH
        + "\nEMPTY_SIGNATURE: "
        + EMPTY_SIGNATURE
        + "\n\n--Deposit contract--"
        //      + "\nDEPOSIT_CONTRACT_ADDRESS: " + DEPOSIT_CONTRACT_ADDRESS
        + "\nDEPOSIT_CONTRACT_TREE_DEPTH: "
        + DEPOSIT_CONTRACT_TREE_DEPTH
        + "\nMIN_DEPOSIT: "
        + MIN_DEPOSIT
        + "\nMAX_DEPOSIT: "
        + MAX_DEPOSIT
        + "\n\n--Initial values--"
        + "\nINITIAL_FORK_VERSION: "
        + INITIAL_FORK_VERSION
        + "\nINITIAL_SLOT_NUMBER: "
        + INITIAL_SLOT_NUMBER
        + "\nZERO_HASH: "
        + ZERO_HASH
        + "\n\n--Time parameters--"
        + "\nSLOT_DURATION: "
        + SLOT_DURATION
        + "\nMIN_ATTESTATION_INCLUSION_DELAY: "
        + MIN_ATTESTATION_INCLUSION_DELAY
        + "\nEPOCH_LENGTH: "
        + EPOCH_LENGTH
        + "\nETH1_DATA_VOTING_PERIOD: "
        + ETH1_DATA_VOTING_PERIOD
        + "\n\n--Reward and penalty quotients--"
        + "\nBASE_REWARD_QUOTIENT: "
        + BASE_REWARD_QUOTIENT
        + "\nWHISTLEBLOWER_REWARD_QUOTIENT: "
        + WHISTLEBLOWER_REWARD_QUOTIENT
        + "\nINCLUDER_REWARD_QUOTIENT: "
        + INCLUDER_REWARD_QUOTIENT
        + "\nINACTIVITY_PENALTY_QUOTIENT: "
        + INACTIVITY_PENALTY_QUOTIENT
        + "\n\n--Status codes--"
        + "\nINITIATED_EXIT: "
        + INITIATED_EXIT
        + "\nWITHDRAWABLE: "
        + WITHDRAWABLE
        + "\n\n--Max operations per block--"
        + "\nMAX_PROPOSER_SLASHINGS: "
        + MAX_PROPOSER_SLASHINGS
        + "\nMAX_CASPER_SLASHINGS: "
        + MAX_CASPER_SLASHINGS
        + "\nMAX_ATTESTATIONS: "
        + MAX_ATTESTATIONS
        + "\nMAX_DEPOSITS: "
        + MAX_DEPOSITS
        + "\nMAX_EXITS: "
        + MAX_EXITS
        + "\n\n--Validator registry delta flags--"
        + "\nACTIVATION: "
        + ACTIVATION
        + "\nEXIT: "
        + EXIT
        + "\n\n--Signature domains--"
        + "\nDOMAIN_DEPOSIT: "
        + DOMAIN_DEPOSIT
        + "\nDOMAIN_ATTESTATION: "
        + DOMAIN_ATTESTATION
        + "\nDOMAIN_PROPOSAL: "
        + DOMAIN_PROPOSAL
        + "\nDOMAIN_EXIT: "
        + DOMAIN_EXIT
        + "\n";
  }
}
