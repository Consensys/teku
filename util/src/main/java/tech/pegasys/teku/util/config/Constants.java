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

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Constants {

  public static final ImmutableList<String> NETWORK_DEFINITIONS =
      ImmutableList.of(
          "mainnet", "minimal", "swift", "pyrmont", "prater", "kintsugi", "less-swift");

  // Misc
  @Deprecated public static UInt64 ETH1_FOLLOW_DISTANCE = UInt64.valueOf(1024);
  @Deprecated public static int MAX_COMMITTEES_PER_SLOT;
  @Deprecated public static int TARGET_COMMITTEE_SIZE;
  @Deprecated public static int MAX_VALIDATORS_PER_COMMITTEE;
  @Deprecated public static int MIN_PER_EPOCH_CHURN_LIMIT;
  @Deprecated public static int CHURN_LIMIT_QUOTIENT;
  @Deprecated public static int SHUFFLE_ROUND_COUNT;
  @Deprecated public static int MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
  @Deprecated public static UInt64 MIN_GENESIS_TIME;
  @Deprecated public static UInt64 HYSTERESIS_QUOTIENT;
  @Deprecated public static UInt64 HYSTERESIS_DOWNWARD_MULTIPLIER;
  @Deprecated public static UInt64 HYSTERESIS_UPWARD_MULTIPLIER;
  @Deprecated public static int PROPORTIONAL_SLASHING_MULTIPLIER;

  // Gwei values
  @Deprecated public static UInt64 MIN_DEPOSIT_AMOUNT;
  @Deprecated public static UInt64 MAX_EFFECTIVE_BALANCE;
  @Deprecated public static UInt64 EJECTION_BALANCE;
  @Deprecated public static UInt64 EFFECTIVE_BALANCE_INCREMENT;

  // Time parameters
  @Deprecated public static UInt64 GENESIS_DELAY;
  @Deprecated public static int SECONDS_PER_SLOT = 12;
  @Deprecated public static int MIN_ATTESTATION_INCLUSION_DELAY;
  @Deprecated public static int SLOTS_PER_EPOCH;
  @Deprecated public static int MIN_SEED_LOOKAHEAD;
  @Deprecated public static int MAX_SEED_LOOKAHEAD;
  @Deprecated public static UInt64 MIN_EPOCHS_TO_INACTIVITY_PENALTY;
  @Deprecated public static int EPOCHS_PER_ETH1_VOTING_PERIOD;
  @Deprecated public static int SLOTS_PER_HISTORICAL_ROOT;
  @Deprecated public static int MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
  @Deprecated public static UInt64 SHARD_COMMITTEE_PERIOD;

  // State list lengths
  @Deprecated public static int EPOCHS_PER_HISTORICAL_VECTOR;
  @Deprecated public static int EPOCHS_PER_SLASHINGS_VECTOR;
  @Deprecated public static int HISTORICAL_ROOTS_LIMIT;
  @Deprecated public static long VALIDATOR_REGISTRY_LIMIT;

  @Deprecated public static int WHISTLEBLOWER_REWARD_QUOTIENT;
  @Deprecated public static UInt64 PROPOSER_REWARD_QUOTIENT;
  @Deprecated public static int MIN_SLASHING_PENALTY_QUOTIENT;

  // Validator
  @Deprecated public static UInt64 SECONDS_PER_ETH1_BLOCK = UInt64.valueOf(14L);

  // Deposit Contract
  @Deprecated public static int DEPOSIT_CHAIN_ID;

  // Networking
  public static final int GOSSIP_MAX_SIZE = 1048576; // bytes
  public static final int GOSSIP_MAX_SIZE_MERGE = 10485760; // bytes
  public static final int MAX_REQUEST_BLOCKS = 1024;
  public static final int MAX_CHUNK_SIZE = 1048576; // bytes
  public static final int MAX_CHUNK_SIZE_MERGE = 10485760; // bytes
  public static final int ATTESTATION_SUBNET_COUNT = 64;
  public static final UInt64 ATTESTATION_PROPAGATION_SLOT_RANGE = UInt64.valueOf(32);
  public static final int MAXIMUM_GOSSIP_CLOCK_DISPARITY = 500; // in ms

  // Teku Networking Specific
  public static final int VALID_BLOCK_SET_SIZE = 1000;
  // Target holding two slots worth of aggregators (16 aggregators, 64 committees and 2 slots)
  public static final int VALID_AGGREGATE_SET_SIZE = 16 * 64 * 2;
  public static final int VALID_VALIDATOR_SET_SIZE = 10000;
  public static final int VALID_CONTRIBUTION_AND_PROOF_SET_SIZE = 10000;
  public static final int VALID_SYNC_COMMITTEE_MESSAGE_SET_SIZE = 10000;
  public static final int NETWORKING_FAILURE_REPEAT_INTERVAL = 3; // in sec

  // Teku specific
  public static final Bytes32 ZERO_HASH = Bytes32.ZERO;
  public static final double TIME_TICKER_REFRESH_RATE = 2; // per sec
  public static final Duration ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT = Duration.ofMillis(500);
  public static final Duration ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT = Duration.ofSeconds(2);
  public static final Duration ETH1_LOCAL_CHAIN_BEHIND_FOLLOW_DISTANCE_WAIT = Duration.ofSeconds(3);
  public static final Duration ETH1_ENDPOINT_MONITOR_SERVICE_POLL_INTERVAL = Duration.ofSeconds(10);
  public static final Duration ETH1_VALID_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(60); // usable
  public static final Duration ETH1_FAILED_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(30); // network or API call failure
  public static final Duration ETH1_INVALID_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(60); // syncing or wrong chainid
  public static final int MAXIMUM_CONCURRENT_ETH1_REQUESTS = 5;
  public static final int REPUTATION_MANAGER_CAPACITY = 1024;
  public static final Duration STORAGE_REQUEST_TIMEOUT = Duration.ofSeconds(60);
  public static final int STORAGE_QUERY_CHANNEL_PARALLELISM = 10; // # threads
  public static final int PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD = 256;

  // Teku Sync
  public static final UInt64 MAX_BLOCK_BY_RANGE_REQUEST_SIZE = UInt64.valueOf(200);
  public static final UInt64 SYNC_BATCH_SIZE = UInt64.valueOf(50);
  public static final int MAX_BLOCKS_PER_MINUTE = 500;

  // Teku Validator Client Specific
  public static final Duration GENESIS_DATA_RETRY_DELAY = Duration.ofSeconds(10);

  static {
    setConstants("minimal");
  }

  /**
   * @deprecated Use tech.pegasys.teku.spec.constants.SpecConfig
   * @param source The source from which to load constants
   */
  @Deprecated
  public static void setConstants(final String source) {
    ConstantsReader.loadConstantsFrom(source);
    SpecDependent.resetAll();
  }
}
