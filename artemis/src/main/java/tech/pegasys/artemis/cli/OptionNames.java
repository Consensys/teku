/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.cli;

public interface OptionNames {
  String CONFIG_FILE_OPTION_NAME = "--config-file";

  // Network
  String NETWORK_OPTION_NAME = "--network";

  // P2P
  String P2P_ENABLED_OPTION_NAME = "--p2p-enabled";
  String P2P_INTERFACE_OPTION_NAME = "--p2p-interface";
  String P2P_PORT_OPTION_NAME = "--p2p-port";
  String P2P_DISCOVERY_ENABLED_OPTION_NAME = "--p2p-discovery-enabled";
  String P2P_DISCOVERY_BOOTNODES_OPTION_NAME = "--p2p-discovery-bootnodes";
  String P2P_ADVERTISED_IP_OPTION_NAME = "--p2p-advertised-ip";
  String P2P_ADVERTISED_PORT_OPTION_NAME = "--p2p-advertised-port";
  String P2P_PRIVATE_KEY_FILE_OPTION_NAME = "--p2p-private-key-file";
  String P2P_PEER_LOWER_BOUND_OPTION_NAME = "--p2p-peer-lower-bound";
  String P2P_PEER_UPPER_BOUND_OPTION_NAME = "--p2p-peer-upper-bound";
  String P2P_STATIC_PEERS_OPTION_NAME = "--p2p-static-peers";

  // Interop
  String X_INTEROP_GENESIS_TIME_OPTION_NAME = "--x-interop-genesis-time";
  String X_INTEROP_OWNED_VALIDATOR_START_INDEX_OPTION_NAME =
      "--x-interop-owned-validator-start-index";
  String X_INTEROP_OWNED_VALIDATOR_COUNT_OPTION_NAME = "--x-interop-owned-validator-count";
  String X_INTEROP_START_STATE_OPTION_NAME = "--x-interop-start-state";
  String X_INTEROP_NUMBER_OF_VALIDATORS_OPTION_NAME = "--x-interop-number-of-validators";
  String X_INTEROP_ENABLED_OPTION_NAME = "--x-interop-enabled";

  // Validator
  String VALIDATORS_KEY_FILE_OPTION_NAME = "--validators-key-file";
  String VALIDATORS_KEYSTORE_FILES_OPTION_NAME = "--validators-key-files";
  String VALIDATORS_KEYSTORE_PASSWORD_FILES_OPTION_NAME = "--validators-key-password-files";

  // Deposit
  String ETH1_DEPOSIT_CONTRACT_ADDRESS_OPTION_NAME = "--eth1-deposit-contract-address";
  String ETH1_ENDPOINT_OPTION_NAME = "--eth1-endpoint";

  // Logging
  String LOG_COLOUR_ENABLED_OPTION_NAME = "--log-colour-enabled";
  String LOG_INCLUDE_EVENTS_ENABLED_OPTION_NAME = "--log-include-events-enabled";
  String LOG_DESTINATION_OPTION_NAME = "--log-destination";
  String LOG_FILE_OPTION_NAME = "--log-file";
  String LOG_FILE_NAME_PATTERN_OPTION_NAME = "--log-file-name-pattern";

  // Output
  String X_TRANSACTION_RECORD_DIRECTORY_OPTION_NAME = "--x-transaction-record-directory";

  // Metrics
  String METRICS_ENABLED_OPTION_NAME = "--metrics-enabled";
  String METRICS_PORT_OPTION_NAME = "--metrics-port";
  String METRICS_INTERFACE_OPTION_NAME = "--metrics-interface";
  String METRICS_CATEGORIES_OPTION_NAME = "--metrics-categories";

  // Database
  String DATA_PATH_OPTION_NAME = "--data-path";
  String DATA_STORAGE_MODE_OPTION_NAME = "--data-storage-mode";

  // Beacon REST API
  String REST_API_PORT_OPTION_NAME = "--rest-api-port";
  String REST_API_DOCS_ENABLED_OPTION_NAME = "--rest-api-docs-enabled";
  String REST_API_ENABLED_OPTION_NAME = "--rest-api-enabled";
  String REST_API_INTERFACE_OPTION_NAME = "--rest-api-interface";
}
