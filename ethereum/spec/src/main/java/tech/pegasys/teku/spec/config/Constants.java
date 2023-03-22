/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.config;

import java.time.Duration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Constants {

  // Networking
  public static final int GOSSIP_MAX_SIZE = 1048576; // bytes
  public static final int GOSSIP_MAX_SIZE_BELLATRIX = 10485760; // bytes
  public static final UInt64 MAX_REQUEST_BLOCKS = UInt64.valueOf(1024);
  public static final int MAX_CHUNK_SIZE = 1048576; // bytes
  public static final int MAX_CHUNK_SIZE_BELLATRIX = 10485760; // bytes
  public static final int ATTESTATION_SUBNET_COUNT = 64;
  public static final UInt64 ATTESTATION_PROPAGATION_SLOT_RANGE = UInt64.valueOf(32);
  public static final int MAXIMUM_GOSSIP_CLOCK_DISPARITY = 500; // in ms

  // Deneb
  public static final UInt64 MAX_REQUEST_BLOCKS_DENEB = UInt64.valueOf(128);
  public static final int MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS = 4096; // ~18 days
  // TODO: remove when blobs decoupling sync is implemented
  public static final int MIN_EPOCHS_FOR_BLOBS_SIDECARS_REQUESTS = 4096;

  // Teku Networking Specific
  public static final int VALID_BLOCK_SET_SIZE = 1000;
  // Target holding two slots worth of aggregators (16 aggregators, 64 committees and 2 slots)
  public static final int VALID_AGGREGATE_SET_SIZE = 16 * 64 * 2;
  // Target 2 different attestation data (aggregators normally agree) for two slots
  public static final int VALID_ATTESTATION_DATA_SET_SIZE = 2 * 64 * 2;
  public static final int VALID_VALIDATOR_SET_SIZE = 10000;
  // Only need to maintain a cache for the current slot, so just needs to be as large as the
  // sync committee size.
  public static final int VALID_CONTRIBUTION_AND_PROOF_SET_SIZE = 512;
  public static final int VALID_SYNC_COMMITTEE_MESSAGE_SET_SIZE = 512;

  public static final Duration ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT = Duration.ofMillis(500);
  public static final Duration ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT = Duration.ofSeconds(2);
  public static final Duration EL_ENGINE_BLOCK_EXECUTION_TIMEOUT = Duration.ofSeconds(8);
  public static final Duration EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT = Duration.ofSeconds(1);
  public static final Duration ETH1_ENDPOINT_MONITOR_SERVICE_POLL_INTERVAL = Duration.ofSeconds(10);
  public static final Duration ETH1_VALID_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(60); // usable
  public static final Duration ETH1_FAILED_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(30); // network or API call failure
  public static final Duration ETH1_INVALID_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(60); // syncing or wrong chainid
  public static final int MAXIMUM_CONCURRENT_ETH1_REQUESTS = 5;
  public static final int MAXIMUM_CONCURRENT_EE_REQUESTS = 5;
  public static final int MAXIMUM_CONCURRENT_EB_REQUESTS = 5;
  public static final int REPUTATION_MANAGER_CAPACITY = 1024;
  public static final Duration STORAGE_REQUEST_TIMEOUT = Duration.ofSeconds(60);
  public static final int STORAGE_QUERY_CHANNEL_PARALLELISM = 10; // # threads
  public static final int PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD = 256;

  // Teku Sync
  public static final UInt64 HISTORICAL_SYNC_BATCH_SIZE = UInt64.valueOf(50);
  public static final UInt64 FORWARD_SYNC_BATCH_SIZE = UInt64.valueOf(50);
  public static final int MAX_BLOCKS_PER_MINUTE = 500;

  // Teku Validator Client Specific
  public static final Duration GENESIS_DATA_RETRY_DELAY = Duration.ofSeconds(10);

  // Builder Specific
  // Maximum duration before timeout for each builder call
  public static final Duration BUILDER_CALL_TIMEOUT = Duration.ofSeconds(8);
  // Individual durations (per method) before timeout for each builder call. They must be less than
  // or equal to BUILDER_CALL_TIMEOUT
  public static final Duration BUILDER_STATUS_TIMEOUT = Duration.ofSeconds(1);
  public static final Duration BUILDER_REGISTER_VALIDATOR_TIMEOUT = Duration.ofSeconds(8);
  public static final Duration BUILDER_PROPOSAL_DELAY_TOLERANCE = Duration.ofSeconds(1);
  public static final Duration BUILDER_GET_PAYLOAD_TIMEOUT = Duration.ofSeconds(3);
  public static final int EPOCHS_PER_VALIDATOR_REGISTRATION_SUBMISSION = 1;
}
