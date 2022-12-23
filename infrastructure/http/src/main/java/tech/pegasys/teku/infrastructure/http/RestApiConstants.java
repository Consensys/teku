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

package tech.pegasys.teku.infrastructure.http;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_PARTIAL_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;

public class RestApiConstants {

  public static final String SLOT = "slot";
  public static final String EPOCH = "epoch";
  public static final String COMMITTEE_INDEX = "committee_index";
  public static final String SUBCOMMITTEE_INDEX = "subcommittee_index";
  public static final String RANDAO_REVEAL = "randao_reveal";
  public static final String GRAFFITI = "graffiti";
  public static final String ATTESTATION_DATA_ROOT = "attestation_data_root";
  public static final String INDEX = "index";
  public static final String TOPICS = "topics";
  public static final String START_PERIOD = "start_period";
  public static final String COUNT = "count";

  public static final String BLOCK_ROOT = "block_root";
  public static final String PARENT_ROOT = "parent_root";
  public static final String BEACON_BLOCK_ROOT = "beacon_block_root";
  public static final String STATUS = "status";

  public static final String TAG_NODE = "Node";
  public static final String TAG_VALIDATOR = "Validator";
  public static final String TAG_BEACON = "Beacon";
  public static final String TAG_EXPERIMENTAL = "Experimental";
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
  public static final String RES_UNSUPPORTED_MEDIA_TYPE = "" + SC_UNSUPPORTED_MEDIA_TYPE;
  public static final String RES_SERVICE_UNAVAILABLE = "" + SC_SERVICE_UNAVAILABLE;

  public static final String INVALID_BODY_SUPPLIED = "Invalid body supplied.";

  public static final String SERVICE_UNAVAILABLE =
      "Beacon node is currently syncing and not serving requests";

  public static final String EPOCH_QUERY_DESCRIPTION = "`uint64` Epoch number to query.";
  public static final String SLOT_QUERY_DESCRIPTION =
      "`UInt64` Slot to query in the canonical chain.";
  public static final String SLOT_PATH_DESCRIPTION =
      "The slot for which the block should be proposed.";

  public static final String COMMITTEE_INDEX_QUERY_DESCRIPTION =
      "`uint64` Committee index to query.";

  public static final String PARAM_PEER_ID = "peer_id";
  public static final String PARAM_PEER_ID_DESCRIPTION =
      "Cryptographic hash of a peer’s public key. [Read more](https://docs.libp2p.io/concepts/peer-id/)";

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

  public static final String PARAM_STATUS_DESCRIPTION =
      "valid values:   pending_initialized, "
          + "  pending_queued, "
          + "  active_ongoing, "
          + "  active_exiting, "
          + "  active_slashed, "
          + "  exited_unslashed, "
          + "  exited_slashed, "
          + "  withdrawal_possible, "
          + "  withdrawal_done, "
          + "  active, "
          + "  pending, "
          + "  exited, "
          + "  withdrawal";

  public static final String SYNCING_STATUS = "syncing_status";
  public static final String SYNCING_STATUS_DESCRIPTION =
      "Customize syncing status instead of default status code (" + SC_PARTIAL_CONTENT + ")";

  public static final String EXECUTION_OPTIMISTIC = "execution_optimistic";
  public static final String FINALIZED = "finalized";
  public static final String TARGET_PEER_COUNT = "target_peer_count";
  public static final String TARGET_PEER_COUNT_DESCRIPTION =
      "Returns "
          + SC_SERVICE_UNAVAILABLE
          + " status code when current peer count is below than target";

  public static final String HEADER_ACCEPT = "Accept";

  public static final String HEADER_CONSENSUS_VERSION = "Eth-Consensus-Version";
  public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";

  public static final String CACHE_NONE = "max-age=0";

  public static final String PUBKEY = "pubkey";
}
