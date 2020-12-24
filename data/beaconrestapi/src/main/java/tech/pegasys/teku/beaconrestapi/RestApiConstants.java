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

package tech.pegasys.teku.beaconrestapi;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_PARTIAL_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

public class RestApiConstants {

  public static final String SLOT = "slot";
  public static final String EPOCH = "epoch";
  public static final String COMMITTEE_INDEX = "committee_index";
  public static final String RANDAO_REVEAL = "randao_reveal";
  public static final String GRAFFITI = "graffiti";
  public static final String ATTESTATION_DATA_ROOT = "attestation_data_root";
  public static final String INDEX = "index";
  public static final String TOPICS = "topics";
  public static final String PARENT_ROOT = "parent_root";
  public static final String STATUS = "status";

  public static final String TAG_V1_NODE = "Node";
  public static final String TAG_V1_VALIDATOR = "Validator";
  public static final String TAG_V1_BEACON = "Beacon";
  public static final String TAG_CONFIG = "Config";
  public static final String TAG_EVENTS = "Events";
  public static final String TAG_VALIDATOR_REQUIRED = "Validator Required Api";
  public static final String TAG_DEBUG = "Debug";
  public static final String TAG_TEKU = "Teku";

  // Use "" + instead of Integer.toString so they are constants and can be used in annotations
  public static final String RES_OK = "" + SC_OK;
  public static final String RES_ACCEPTED = "" + SC_ACCEPTED;
  public static final String RES_NO_CONTENT = "" + SC_NO_CONTENT;
  public static final String RES_PARTIAL_CONTENT = "" + SC_PARTIAL_CONTENT;
  public static final String RES_BAD_REQUEST = "" + SC_BAD_REQUEST;
  public static final String RES_FORBIDDEN = "" + SC_FORBIDDEN;
  public static final String RES_NOT_FOUND = "" + SC_NOT_FOUND;
  public static final String RES_INTERNAL_ERROR = "" + SC_INTERNAL_SERVER_ERROR;
  public static final String RES_SERVICE_UNAVAILABLE = "" + SC_SERVICE_UNAVAILABLE;

  public static final String INVALID_BODY_SUPPLIED = "Invalid body supplied.";

  public static final String SERVICE_UNAVAILABLE =
      "Beacon node is currently syncing and not serving requests";

  public static final String EPOCH_QUERY_DESCRIPTION = "`uint64` Epoch number to query.";
  public static final String SLOT_QUERY_DESCRIPTION =
      "`UInt64` Slot to query in the canonical chain.";

  public static final String COMMITTEE_INDEX_QUERY_DESCRIPTION =
      "`uint64` Committee index to query.";

  public static final String PARAM_BLOCK_ID = "block_id";
  public static final String PARAM_BLOCK_ID_DESCRIPTION =
      "Block identifier. Can be one of: "
          + "\"head\" (canonical head in node's view), "
          + "\"genesis\", "
          + "\"finalized\", "
          + "&lt;slot&gt;, "
          + "&lt;hex encoded blockRoot with 0x prefix&gt;.";
  public static final String PARAM_STATE_ID = "state_id";
  public static final String PARAM_STATE_ID_DESCRIPTION =
      "State identifier. Can be one of: "
          + "\"head\" (canonical head in node's view), "
          + "\"genesis\", "
          + "\"finalized\", "
          + "\"justified\", "
          + "&lt;slot&gt;, "
          + "&lt;hex encoded stateRoot with 0x prefix&gt;.";

  public static final String PARAM_ID = "id";
  public static final String PARAM_VALIDATOR_ID = "validator_id";
  public static final String PARAM_VALIDATOR_DESCRIPTION =
      "Either hex encoded public key (with 0x prefix) or validator index";

  public static final String HEADER_ACCEPT = "Accept";
  public static final String HEADER_ACCEPT_JSON = "application/json";
  public static final String HEADER_ACCEPT_OCTET = "application/octet-stream";
}
