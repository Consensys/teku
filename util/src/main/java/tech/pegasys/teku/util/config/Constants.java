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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes4;

public class Constants {

  public static final ImmutableList<String> NETWORK_DEFINITIONS =
      ImmutableList.of("mainnet", "minimal", "swift", "pyrmont", "prater", "less-swift");

  // Non-configurable constants
  @Deprecated public static final long GENESIS_SLOT = 0;
  @Deprecated public static final long GENESIS_EPOCH = 0;
  @Deprecated public static final UInt64 FAR_FUTURE_EPOCH = UInt64.MAX_VALUE;
  @Deprecated public static final UInt64 BASE_REWARDS_PER_EPOCH = UInt64.valueOf(4);
  @Deprecated public static final int DEPOSIT_CONTRACT_TREE_DEPTH = 32;
  @Deprecated public static final int JUSTIFICATION_BITS_LENGTH = 4;

  // Phase0 constants which may exist in legacy config files, but are no longer configurable
  @Deprecated public static final Bytes BLS_WITHDRAWAL_PREFIX = Bytes.fromHexString("0x00");
  @Deprecated public static final int TARGET_AGGREGATORS_PER_COMMITTEE = 16;
  @Deprecated public static final int RANDOM_SUBNETS_PER_VALIDATOR = 1;
  @Deprecated public static final int EPOCHS_PER_RANDOM_SUBNET_SUBSCRIPTION = 256;

  @Deprecated
  public static final Bytes4 DOMAIN_BEACON_PROPOSER = new Bytes4(Bytes.fromHexString("0x00000000"));

  @Deprecated
  public static final Bytes4 DOMAIN_BEACON_ATTESTER = new Bytes4(Bytes.fromHexString("0x01000000"));

  @Deprecated
  public static final Bytes4 DOMAIN_RANDAO = new Bytes4(Bytes.fromHexString("0x02000000"));

  @Deprecated
  public static final Bytes4 DOMAIN_DEPOSIT = new Bytes4(Bytes.fromHexString("0x03000000"));

  @Deprecated
  public static final Bytes4 DOMAIN_VOLUNTARY_EXIT = new Bytes4(Bytes.fromHexString("0x04000000"));

  @Deprecated
  public static final Bytes4 DOMAIN_SELECTION_PROOF = Bytes4.fromHexString("0x05000000");

  @Deprecated
  public static final Bytes4 DOMAIN_AGGREGATE_AND_PROOF = Bytes4.fromHexString("0x06000000");

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

  // Initial values
  @Deprecated public static Bytes4 GENESIS_FORK_VERSION = Bytes4.fromHexString("0x00000000");

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

  // Reward and penalty quotients
  @Deprecated public static int BASE_REWARD_FACTOR;
  @Deprecated public static int WHISTLEBLOWER_REWARD_QUOTIENT;
  @Deprecated public static UInt64 PROPOSER_REWARD_QUOTIENT;
  @Deprecated public static UInt64 INACTIVITY_PENALTY_QUOTIENT;
  @Deprecated public static int MIN_SLASHING_PENALTY_QUOTIENT;

  // Max transactions per block
  @Deprecated public static int MAX_PROPOSER_SLASHINGS;
  @Deprecated public static int MAX_ATTESTER_SLASHINGS;
  @Deprecated public static int MAX_ATTESTATIONS;
  @Deprecated public static int MAX_DEPOSITS;
  @Deprecated public static int MAX_VOLUNTARY_EXITS = 16;

  // Validator
  @Deprecated public static UInt64 SECONDS_PER_ETH1_BLOCK = UInt64.valueOf(14L);

  // Fork Choice
  @Deprecated public static int SAFE_SLOTS_TO_UPDATE_JUSTIFIED = 8;

  // Deposit Contract
  @Deprecated public static int DEPOSIT_CHAIN_ID;
  @Deprecated public static int DEPOSIT_NETWORK_ID;

  @Deprecated
  public static Bytes DEPOSIT_CONTRACT_ADDRESS =
      Bytes.fromHexString("0x1234567890123456789012345678901234567890");

  // SSZ
  public static final UInt64 BYTES_PER_LENGTH_OFFSET = UInt64.valueOf(4L);

  // Networking
  public static final int GOSSIP_MAX_SIZE = 1048576; // bytes
  public static final int MAX_REQUEST_BLOCKS = 1024;
  public static final int MAX_CHUNK_SIZE = 1048576; // bytes
  public static final int ATTESTATION_SUBNET_COUNT = 64;
  public static final UInt64 ATTESTATION_PROPAGATION_SLOT_RANGE = UInt64.valueOf(32);
  public static final int MAXIMUM_GOSSIP_CLOCK_DISPARITY = 500; // in ms

  // Teku Networking Specific
  public static final int VALID_BLOCK_SET_SIZE = 1000;
  public static final int VALID_ATTESTATION_SET_SIZE = 1000;
  public static final int VALID_AGGREGATE_SET_SIZE = 1000;
  public static final int VALID_VALIDATOR_SET_SIZE = 10000;
  public static final int VALID_CONTRIBUTION_AND_PROOF_SET_SIZE = 10000;
  public static final int VALID_SYNC_COMMITTEE_SIGNATURE_SET_SIZE = 10000;
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
  public static final int ATTESTATION_RETENTION_EPOCHS = 2;
  public static final int OPERATION_POOL_SIZE = 1000;

  // Teku Sync
  public static final UInt64 MAX_BLOCK_BY_RANGE_REQUEST_SIZE = UInt64.valueOf(200);
  public static final UInt64 SYNC_BATCH_SIZE = UInt64.valueOf(50);
  public static final int MAX_BLOCKS_PER_MINUTE = 500;

  // Teku Validator Client Specific
  public static final Duration FORK_RETRY_DELAY = Duration.ofSeconds(10);
  public static final Duration FORK_REFRESH_TIME = Duration.ofMinutes(5);
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
