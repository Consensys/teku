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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

public class Constants {

  // Non-configurable constants
  public static UnsignedLong FAR_FUTURE_EPOCH = UnsignedLong.MAX_VALUE;
  public static int BASE_REWARDS_PER_EPOCH = 4;
  public static int DEPOSIT_CONTRACT_TREE_DEPTH = 32;
  public static int JUSTIFICATION_BITS_LENGTH = 4;

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
  public static Bytes4 GENESIS_FORK_VERSION = Bytes4.fromHexString("0x00000000");
  public static long GENESIS_SLOT;
  public static long GENESIS_EPOCH;
  public static Bytes BLS_WITHDRAWAL_PREFIX;

  // Time parameters
  public static int MIN_GENESIS_DELAY = 86400;
  public static int SECONDS_PER_SLOT = 12;
  public static int MIN_ATTESTATION_INCLUSION_DELAY;
  public static int SLOTS_PER_EPOCH;
  public static int MIN_SEED_LOOKAHEAD;
  public static int MAX_SEED_LOOKAHEAD;
  public static int MIN_EPOCHS_TO_INACTIVITY_PENALTY;
  public static int SLOTS_PER_ETH1_VOTING_PERIOD;
  public static int SLOTS_PER_HISTORICAL_ROOT;
  public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
  public static int PERSISTENT_COMMITTEE_PERIOD;
  public static int MAX_EPOCHS_PER_CROSSLINK;
  public static int EPOCHS_PER_CUSTODY_PERIOD;
  public static int CUSTODY_PERIOD_TO_RANDAO_PADDING;

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
  public static Bytes4 DOMAIN_CUSTODY_BIT_CHALLENGE = Bytes4.fromHexString("0x06000000");
  public static Bytes4 DOMAIN_SHARD_PROPOSER = Bytes4.fromHexString("0x80000000");
  public static Bytes4 DOMAIN_SHARD_ATTESTER = Bytes4.fromHexString("0x81000000");

  // Honest Validator
  public static UnsignedLong TARGET_AGGREGATORS_PER_COMMITTEE = UnsignedLong.valueOf(16);
  public static UnsignedLong SECONDS_PER_ETH1_BLOCK = UnsignedLong.valueOf(14L);

  // Deposit
  public static String DEPOSIT_NORMAL = "normal";
  public static String DEPOSIT_TEST = "test";

  // Fork Choice
  public static int SAFE_SLOTS_TO_UPDATE_JUSTIFIED = 8;

  // Validator
  public static int RANDOM_SUBNETS_PER_VALIDATOR = 1;
  public static int EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION = 256;

  // Sync
  public static UnsignedLong MAX_BLOCK_BY_RANGE_REQUEST_SIZE = UnsignedLong.valueOf(200);

  public static Bytes DEPOSIT_CONTRACT_ADDRESS =
      Bytes.fromHexString("0x1234567890123456789012345678901234567890");

  public static int EARLY_DERIVED_SECRET_PENALTY_MAX_FUTURE_EPOCHS;

  public static UnsignedLong BYTES_PER_LENGTH_OFFSET = UnsignedLong.valueOf(4L);

  public static UnsignedLong ETH1_FOLLOW_DISTANCE = UnsignedLong.valueOf(1024);

  // Phase 1
  public static int SHARD_SLOTS_PER_BEACON_SLOT;
  public static int EPOCHS_PER_SHARD_PERIOD;
  public static int PHASE_1_FORK_EPOCH;
  public static int PHASE_1_FORK_SLOT;

  // Artemis specific
  public static Bytes32 ZERO_HASH = Bytes32.ZERO;
  public static double TIME_TICKER_REFRESH_RATE = 2; // per sec
  public static UnsignedLong GENESIS_START_DELAY = UnsignedLong.valueOf(5);
  public static int COMMITTEE_INDEX_SUBSCRIPTION_LENGTH = 2; // in epochs
  public static UnsignedLong ETH1_REQUEST_BUFFER = UnsignedLong.valueOf(10); // in sec
  public static long ETH1_CACHE_STARTUP_RETRY_TIMEOUT = 10; // in sec
  public static long ETH1_CACHE_STARTUP_RETRY_GIVEUP = 5; // in #
  public static long ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT = 500; // in milli sec
  public static long ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT = 2; // in sec
  public static long ETH1_SUBSCRIPTION_RETRY_TIMEOUT = 5; // in sec
  public static final int MAXIMUM_CONCURRENT_ETH1_REQUESTS = 5;
  public static final int REPUTATION_MANAGER_CAPACITY = 100;
  public static long STORAGE_REQUEST_TIMEOUT = 3; // in sec
  public static int STORAGE_QUERY_CHANNEL_PARALLELISM = 10; // # threads

  // Teku Validator Client Specific
  public static long VALIDATOR_DUTIES_TIMEOUT = 15; // in sec
  public static final long FORK_RETRY_DELAY_SECONDS = 10; // in sec
  public static final long FORK_REFRESH_TIME_SECONDS = TimeUnit.MINUTES.toSeconds(5); // in sec

  static {
    setConstants("minimal");
  }

  public static void setConstants(final String source) {
    try (final InputStream input = createInputStream(source)) {
      ConstantsReader.loadConstantsFrom(input);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to load constants from " + source, e);
    }
  }

  private static InputStream createInputStream(final String source) throws IOException {
    if (source.contains(":")) {
      return new URL(source).openStream();
    } else if ("mainnet".equals(source) || "minimal".equals(source)) {
      return Constants.class.getResourceAsStream(source + ".yaml");
    } else {
      // Treat it as a file
      return Files.newInputStream(Path.of(source));
    }
  }
}
