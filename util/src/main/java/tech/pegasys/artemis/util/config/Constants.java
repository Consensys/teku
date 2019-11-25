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

package tech.pegasys.artemis.util.config;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class Constants {

  // Non-configurable constants
  public static UnsignedLong FAR_FUTURE_EPOCH = UnsignedLong.MAX_VALUE;
  public static int BASE_REWARDS_PER_EPOCH = 4;
  public static int DEPOSIT_CONTRACT_TREE_DEPTH = 32;
  public static int SECONDS_PER_DAY = 86400;
  public static int JUSTIFICATION_BITS_LENGTH = 4;
  public static String ENDIANNESS = "little";

  // Misc
  public static int MAX_COMMITTEES_PER_SLOT;
  public static int TARGET_COMMITTEE_SIZE;
  public static int MAX_VALIDATORS_PER_COMMITTEE;
  public static int MIN_PER_EPOCH_CHURN_LIMIT;
  public static int CHURN_LIMIT_QUOTIENT;
  public static int SHUFFLE_ROUND_COUNT;
  public static int MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
  public static UnsignedLong MIN_GENESIS_TIME;

  // Gwei values
  public static long MIN_DEPOSIT_AMOUNT;
  public static long MAX_EFFECTIVE_BALANCE;
  public static long EJECTION_BALANCE;
  public static long EFFECTIVE_BALANCE_INCREMENT;

  // Initial values
  public static long GENESIS_SLOT;
  public static long GENESIS_EPOCH;
  public static Bytes BLS_WITHDRAWAL_PREFIX;

  // Time parameters
  public static int SECONDS_PER_SLOT = 12;
  public static int MIN_ATTESTATION_INCLUSION_DELAY;
  public static int SLOTS_PER_EPOCH;
  public static int MIN_SEED_LOOKAHEAD;
  public static int MAX_SEED_LOOKAHEAD;
  public static int SLOTS_PER_ETH1_VOTING_PERIOD;
  public static int SLOTS_PER_HISTORICAL_ROOT;
  public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
  public static int PERSISTENT_COMMITTEE_PERIOD;
  public static int MIN_EPOCHS_TO_INACTIVITY_PENALTY;

  // State list lengths
  public static int EPOCHS_PER_HISTORICAL_VECTOR;
  public static int EPOCHS_PER_SLASHINGS_VECTOR;
  public static int HISTORICAL_ROOTS_LIMIT;
  public static long VALIDATOR_REGISTRY_LIMIT;

  // Reward and penalty quotients
  public static int BASE_REWARD_FACTOR;
  public static int WHISTLEBLOWER_REWARD_QUOTIENT;
  public static int PROPOSER_REWARD_QUOTIENT;
  public static int INACTIVITY_PENALTY_QUOTIENT;
  public static int MIN_SLASHING_PENALTY_QUOTIENT;

  // Max transactions per block
  public static int MAX_PROPOSER_SLASHINGS;
  public static int MAX_ATTESTER_SLASHINGS;
  public static int MAX_ATTESTATIONS;
  public static int MAX_DEPOSITS;
  public static int MAX_VOLUNTARY_EXITS = 16;

  // Signature domains
  public static Bytes4 DOMAIN_BEACON_PROPOSER = new Bytes4(Bytes.fromHexString("0x00000000"));
  public static Bytes4 DOMAIN_BEACON_ATTESTER = new Bytes4(Bytes.fromHexString("0x01000000"));
  public static Bytes4 DOMAIN_RANDAO = new Bytes4(Bytes.fromHexString("0x02000000"));
  public static Bytes4 DOMAIN_DEPOSIT = new Bytes4(Bytes.fromHexString("0x03000000"));
  public static Bytes4 DOMAIN_VOLUNTARY_EXIT = new Bytes4(Bytes.fromHexString("0x04000000"));

  // Honest Validator
  public static UnsignedLong TARGET_AGGREGATORS_PER_COMMITTEE = UnsignedLong.valueOf(16);

  // Artemis specific
  public static String SIM_DEPOSIT_VALUE = "1000000000000000000";
  public static int DEPOSIT_DATA_SIZE = 512; //
  public static int VALIDATOR_CLIENT_PORT_BASE = 50000;
  public static Bytes32 ZERO_HASH = Bytes32.ZERO;
  public static double TIME_TICKER_REFRESH_RATE = 2; // per sec
  public static UnsignedLong GENESIS_TIME = UnsignedLong.MAX_VALUE;
  public static UnsignedLong GENESIS_START_DELAY = UnsignedLong.valueOf(5);

  // Deposit
  public static String DEPOSIT_NORMAL = "normal";
  public static String DEPOSIT_TEST = "test";
  public static String DEPOSIT_SIM = "simulation";

  // Fork Choice
  public static int SAFE_SLOTS_TO_UPDATE_JUSTIFIED = 8;

  // Validator
  public static int RANDOM_SUBNETS_PER_VALIDATOR = 1;
  public static int EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION = 256;

  public static Bytes DEPOSIT_CONTRACT_ADDRESS =
      Bytes.fromHexString("0x1234567890123456789012345678901234567890");

  public static int EARLY_DERIVED_SECRET_PENALTY_MAX_FUTURE_EPOCHS;

  public static BLSSignature EMPTY_SIGNATURE = BLSSignature.empty();
  public static UnsignedLong BYTES_PER_LENGTH_OFFSET = UnsignedLong.valueOf(4L);

  public static UnsignedLong ETH1_FOLLOW_DISTANCE = UnsignedLong.valueOf(1024);

  static {
    setConstants("minimal");
  }

  @SuppressWarnings("rawtypes")
  public static void setConstants(String Constants) {
    if (Constants.equals("mainnet")) {

      // Mainnet settings

      // Misc
      MAX_COMMITTEES_PER_SLOT = 64;
      TARGET_COMMITTEE_SIZE = 128;
      MAX_VALIDATORS_PER_COMMITTEE = 2048;
      MIN_PER_EPOCH_CHURN_LIMIT = 4;
      CHURN_LIMIT_QUOTIENT = 65536;
      SHUFFLE_ROUND_COUNT = 90;
      MIN_GENESIS_ACTIVE_VALIDATOR_COUNT = 16384;
      MIN_GENESIS_TIME = UnsignedLong.valueOf(1578009600);

      // Gwei values
      MIN_DEPOSIT_AMOUNT = 1000000000L;
      MAX_EFFECTIVE_BALANCE = 32000000000L;
      EJECTION_BALANCE = 16000000000L;
      EFFECTIVE_BALANCE_INCREMENT = 1000000000L;

      // Initial values
      GENESIS_SLOT = 0;
      GENESIS_EPOCH = 0;
      BLS_WITHDRAWAL_PREFIX = Bytes.wrap(new byte[1]);

      // Time parameters
      MIN_ATTESTATION_INCLUSION_DELAY = 1;
      SLOTS_PER_EPOCH = 32;
      MIN_SEED_LOOKAHEAD = 1;
      MAX_SEED_LOOKAHEAD = 4;
      SLOTS_PER_ETH1_VOTING_PERIOD = 1024;
      SLOTS_PER_HISTORICAL_ROOT = 8192;
      MIN_VALIDATOR_WITHDRAWABILITY_DELAY = 256;
      PERSISTENT_COMMITTEE_PERIOD = 2048;
      MIN_EPOCHS_TO_INACTIVITY_PENALTY = 4;
      EARLY_DERIVED_SECRET_PENALTY_MAX_FUTURE_EPOCHS = 16384;

      // State list lengths
      EPOCHS_PER_HISTORICAL_VECTOR = 65536;
      EPOCHS_PER_SLASHINGS_VECTOR = 8192;
      HISTORICAL_ROOTS_LIMIT = 16777216;
      VALIDATOR_REGISTRY_LIMIT = 1099511627776L;

      // Reward and penalty quotients
      BASE_REWARD_FACTOR = 64;
      WHISTLEBLOWER_REWARD_QUOTIENT = 512;
      PROPOSER_REWARD_QUOTIENT = 8;
      INACTIVITY_PENALTY_QUOTIENT = 33554432;
      MIN_SLASHING_PENALTY_QUOTIENT = 32;

      // Max transactions per block
      MAX_PROPOSER_SLASHINGS = 16;
      MAX_ATTESTER_SLASHINGS = 1;
      MAX_ATTESTATIONS = 128;
      MAX_DEPOSITS = 16;
      MAX_VOLUNTARY_EXITS = 16;

    } else {

      // Minimal settings

      // Misc
      MAX_COMMITTEES_PER_SLOT = 4;
      TARGET_COMMITTEE_SIZE = 4;
      MAX_VALIDATORS_PER_COMMITTEE = 2048;
      MIN_PER_EPOCH_CHURN_LIMIT = 4;
      CHURN_LIMIT_QUOTIENT = 65536;
      SHUFFLE_ROUND_COUNT = 10;
      MIN_GENESIS_ACTIVE_VALIDATOR_COUNT = 64;
      MIN_GENESIS_TIME = UnsignedLong.valueOf(1578009600);

      // Gwei values
      MIN_DEPOSIT_AMOUNT = 1000000000L;
      MAX_EFFECTIVE_BALANCE = 32000000000L;
      EJECTION_BALANCE = 16000000000L;
      EFFECTIVE_BALANCE_INCREMENT = 1000000000L;

      // Initial values
      GENESIS_SLOT = 0;
      GENESIS_EPOCH = 0;
      BLS_WITHDRAWAL_PREFIX = Bytes.wrap(new byte[1]);

      // Time parameters
      MIN_ATTESTATION_INCLUSION_DELAY = 1;
      SLOTS_PER_EPOCH = 8;
      MIN_SEED_LOOKAHEAD = 1;
      MAX_SEED_LOOKAHEAD = 4;
      SLOTS_PER_ETH1_VOTING_PERIOD = 16;
      SLOTS_PER_HISTORICAL_ROOT = 64;
      MIN_VALIDATOR_WITHDRAWABILITY_DELAY = 256;
      PERSISTENT_COMMITTEE_PERIOD = 2048;
      MIN_EPOCHS_TO_INACTIVITY_PENALTY = 4;
      EARLY_DERIVED_SECRET_PENALTY_MAX_FUTURE_EPOCHS = 4096;

      // State list lengths
      EPOCHS_PER_HISTORICAL_VECTOR = 64;
      EPOCHS_PER_SLASHINGS_VECTOR = 64;
      HISTORICAL_ROOTS_LIMIT = 16777216;
      VALIDATOR_REGISTRY_LIMIT = 1099511627776L;

      // Reward and penalty quotients
      BASE_REWARD_FACTOR = 64;
      WHISTLEBLOWER_REWARD_QUOTIENT = 512;
      PROPOSER_REWARD_QUOTIENT = 8;
      INACTIVITY_PENALTY_QUOTIENT = 33554432;
      MIN_SLASHING_PENALTY_QUOTIENT = 32;

      // Max transactions per block
      MAX_PROPOSER_SLASHINGS = 16;
      MAX_ATTESTER_SLASHINGS = 1;
      MAX_ATTESTATIONS = 128;
      MAX_DEPOSITS = 16;
      MAX_VOLUNTARY_EXITS = 16;
    }
  }
}
