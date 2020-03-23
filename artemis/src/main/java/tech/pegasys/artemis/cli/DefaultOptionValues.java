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

import java.util.ArrayList;

public interface DefaultOptionValues {
  String DEFAULT_CONFIG_FILE = "./config/config.toml";

  // Network
  String DEFAULT_NETWORK = "minimal";

  // P2P
  boolean DEFAULT_P2P_ENABLED = true;
  String DEFAULT_P2P_INTERFACE = "0.0.0.0";
  int DEFAULT_P2P_PORT = 30303;
  boolean DEFAULT_P2P_DISCOVERY_ENABLED = true;
  ArrayList<String> DEFAULT_P2P_DISCOVERY_BOOTNODES =
      new ArrayList<>(); // depends on network option
  String DEFAULT_P2P_ADVERTISED_IP = "127.0.0.1";
  int DEFAULT_P2P_ADVERTISED_PORT = DEFAULT_P2P_PORT;
  String DEFAULT_P2P_PRIVATE_KEY_FILE = null;
  int DEFAULT_P2P_PEER_LOWER_BOUND = 20;
  int DEFAULT_P2P_PEER_UPPER_BOUND = 30;
  ArrayList<String> DEFAULT_P2P_STATIC_PEERS = new ArrayList<>();

  // Interop
  Integer DEFAULT_X_INTEROP_GENESIS_TIME = null;
  int DEFAULT_X_INTEROP_OWNED_VALIDATOR_START_INDEX = 0;
  int DEFAULT_X_INTEROP_OWNED_VALIDATOR_COUNT = 0;
  String DEFAULT_X_INTEROP_START_STATE = "";
  int DEFAULT_X_INTEROP_NUMBER_OF_VALIDATORS = 64;
  boolean DEFAULT_X_INTEROP_ENABLED = false;

  // Validator
  String DEFAULT_VALIDATORS_KEY_FILE = null;
  ArrayList<String> DEFAULT_VALIDATORS_KEYSTORE_FILES = new ArrayList<>();
  ArrayList<String> DEFAULT_VALIDATORS_KEYSTORE_PASSWORD_FILES = new ArrayList<>();

  // Deposit
  String DEFAULT_ETH1_DEPOSIT_CONTRACT_ADDRESS = null; // depends on network option
  String DEFAULT_ETH1_ENDPOINT =
      null; // required but could change as technically not needed if no validators are running

  // Logging
  boolean DEFAULT_LOG_COLOUR_ENABLED = true;
  boolean DEFAULT_LOG_INCLUDE_EVENTS_ENABLED = true;
  String DEFAULT_LOG_DESTINATION = "both";
  String DEFAULT_LOG_FILE = "teku.log";
  String DEFAULT_LOG_FILE_NAME_PATTERN = "teku_%d{yyyy-MM-dd}.log";

  // Output
  String DEFAULT_X_TRANSACTION_RECORD_DIRECTORY = null;

  // Metrics
  boolean DEFAULT_METRICS_ENABLED = false;
  int DEFAULT_METRICS_PORT = 8008;
  String DEFAULT_METRICS_INTERFACE = "127.0.0.1";
  ArrayList<String> DEFAULT_METRICS_CATEGORIES = new ArrayList<>();

  // Database
  String DEFAULT_DATA_PATH = ".";
  String DEFAULT_DATA_STORAGE_MODE = "prune";

  // Beacon REST API
  int DEFAULT_REST_API_PORT = 5051;
  boolean DEFAULT_REST_API_DOCS_ENABLED = false;
  boolean DEFAULT_REST_API_ENABLED = false;
  String DEFAULT_REST_API_INTERFACE = "127.0.0.1";
}
