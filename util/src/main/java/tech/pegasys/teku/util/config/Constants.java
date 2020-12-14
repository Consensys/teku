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

package tech.pegasys.teku.util.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class Constants {

  static final String[] NETWORK_DEFINITIONS = {
    "mainnet", "minimal", "swift", "medalla", "toledo", "pyrmont", "less-swift"
  };

  // Non-configurable constants
  public static final UInt64 FAR_FUTURE_EPOCH = UInt64.MAX_VALUE;
  public static final UInt64 BASE_REWARDS_PER_EPOCH = UInt64.valueOf(4);
  public static final int DEPOSIT_CONTRACT_TREE_DEPTH = 32;
  public static final int JUSTIFICATION_BITS_LENGTH = 4;

  public static String CONFIG_NAME;

  // Misc
  public static int MAX_COMMITTEES_PER_SLOT;
  public static int TARGET_COMMITTEE_SIZE;
  public static int MAX_VALIDATORS_PER_COMMITTEE;
  public static int MIN_PER_EPOCH_CHURN_LIMIT;
  public static int CHURN_LIMIT_QUOTIENT;
  public static int SHUFFLE_ROUND_COUNT;
  public static int MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
  public static UInt64 MIN_GENESIS_TIME;
  public static UInt64 HYSTERESIS_QUOTIENT;
  public static UInt64 HYSTERESIS_DOWNWARD_MULTIPLIER;
  public static UInt64 HYSTERESIS_UPWARD_MULTIPLIER;
  public static int PROPORTIONAL_SLASHING_MULTIPLIER;
  public static final int MAX_REQUEST_BLOCKS = 1024;

  // Gwei values
  public static UInt64 MIN_DEPOSIT_AMOUNT;
  public static UInt64 MAX_EFFECTIVE_BALANCE;
  public static UInt64 EJECTION_BALANCE;
  public static UInt64 EFFECTIVE_BALANCE_INCREMENT;

  // Initial values
  public static Bytes4 GENESIS_FORK_VERSION = Bytes4.fromHexString("0x00000000");
  public static final long GENESIS_SLOT = 0;
  public static final long GENESIS_EPOCH = 0;
  public static Bytes BLS_WITHDRAWAL_PREFIX;

  // Time parameters
  public static UInt64 GENESIS_DELAY;
  public static int SECONDS_PER_SLOT = 12;
  public static int MIN_ATTESTATION_INCLUSION_DELAY;
  public static int SLOTS_PER_EPOCH;
  public static int MIN_SEED_LOOKAHEAD;
  public static int MAX_SEED_LOOKAHEAD;
  public static UInt64 MIN_EPOCHS_TO_INACTIVITY_PENALTY;
  public static int EPOCHS_PER_ETH1_VOTING_PERIOD;
  public static int SLOTS_PER_HISTORICAL_ROOT;
  public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
  public static UInt64 SHARD_COMMITTEE_PERIOD;

  // State list lengths
  public static int EPOCHS_PER_HISTORICAL_VECTOR;
  public static int EPOCHS_PER_SLASHINGS_VECTOR;
  public static int HISTORICAL_ROOTS_LIMIT;
  public static long VALIDATOR_REGISTRY_LIMIT;

  // Reward and penalty quotients
  public static int BASE_REWARD_FACTOR;
  public static int WHISTLEBLOWER_REWARD_QUOTIENT;
  public static UInt64 PROPOSER_REWARD_QUOTIENT;
  public static UInt64 INACTIVITY_PENALTY_QUOTIENT;
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
  public static Bytes4 DOMAIN_SELECTION_PROOF;
  public static Bytes4 DOMAIN_AGGREGATE_AND_PROOF;

  // Honest Validator
  public static int TARGET_AGGREGATORS_PER_COMMITTEE = 16;
  public static UInt64 SECONDS_PER_ETH1_BLOCK = UInt64.valueOf(14L);

  // Fork Choice
  public static int SAFE_SLOTS_TO_UPDATE_JUSTIFIED = 8;

  // Validator
  public static int RANDOM_SUBNETS_PER_VALIDATOR = 1;
  public static int EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION = 256;

  public static int DEPOSIT_CHAIN_ID;
  public static int DEPOSIT_NETWORK_ID;
  public static Bytes DEPOSIT_CONTRACT_ADDRESS =
      Bytes.fromHexString("0x1234567890123456789012345678901234567890");

  public static final UInt64 BYTES_PER_LENGTH_OFFSET = UInt64.valueOf(4L);

  public static UInt64 ETH1_FOLLOW_DISTANCE = UInt64.valueOf(1024);

  // Networking
  public static final int GOSSIP_MAX_SIZE = 1048576; // bytes
  public static final int MAX_CHUNK_SIZE = 1048576; // bytes
  public static final int ATTESTATION_SUBNET_COUNT = 64;
  public static final int TTFB_TIMEOUT = 5; // in sec
  public static final int RESP_TIMEOUT = 10; // in sec
  public static final UInt64 ATTESTATION_PROPAGATION_SLOT_RANGE = UInt64.valueOf(32);
  public static final int MAXIMUM_GOSSIP_CLOCK_DISPARITY = 500; // in ms

  // Teku Networking Specific
  public static final int VALID_BLOCK_SET_SIZE = 1000;
  public static final int VALID_ATTESTATION_SET_SIZE = 1000;
  public static final int VALID_AGGREGATE_SET_SIZE = 1000;
  public static final int VALID_VALIDATOR_SET_SIZE = 10000;
  public static final int NETWORKING_FAILURE_REPEAT_INTERVAL = 3; // in sec

  // Teku specific
  public static final Bytes32 ZERO_HASH = Bytes32.ZERO;
  public static final double TIME_TICKER_REFRESH_RATE = 2; // per sec
  public static final long ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT = 500; // in milli sec
  public static final long ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT = 2; // in sec
  public static final long ETH1_LOCAL_CHAIN_BEHIND_FOLLOW_DISTANCE_WAIT = 3; // in sec
  public static final int MAXIMUM_CONCURRENT_ETH1_REQUESTS = 5;
  public static final int REPUTATION_MANAGER_CAPACITY = 1024;
  public static final long STORAGE_REQUEST_TIMEOUT = 60; // in sec
  public static final int STORAGE_QUERY_CHANNEL_PARALLELISM = 10; // # threads
  public static final int PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD = 256;
  public static final int ATTESTATION_RETENTION_EPOCHS = 2;
  public static final int OPERATION_POOL_SIZE = 1000;

  // Teku Sync
  public static final UInt64 MAX_BLOCK_BY_RANGE_REQUEST_SIZE = UInt64.valueOf(200);
  public static final UInt64 SYNC_BATCH_SIZE = UInt64.valueOf(50);
  public static final int MAX_BLOCKS_PER_MINUTE = 500;

  // Teku Validator Client Specific
  public static final long FORK_RETRY_DELAY_SECONDS = 10; // in sec
  public static final long FORK_REFRESH_TIME_SECONDS = TimeUnit.MINUTES.toSeconds(5); // in sec
  public static final long GENESIS_DATA_RETRY_DELAY_SECONDS = 10; // in sec

  public static final Map<String, Object> CONFIG_ITEM_MAP = new HashMap<>();

  static {
    setConstants("minimal");
  }

  public static void setConstants(final String source) {
    try (final InputStream input = createInputStream(source)) {
      ConstantsReader.loadConstantsFrom(input);
    } catch (IOException e) {
      throw new InvalidConfigurationException("Failed to load constants from " + source, e);
    }
  }

  private static InputStream createInputStream(final String source) throws IOException {
    return ResourceLoader.classpathUrlOrFile(
            Constants.class, name -> name + ".yaml", NETWORK_DEFINITIONS)
        .load(source)
        .orElseThrow(() -> new FileNotFoundException("Could not load constants from " + source));
  }
}
