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

public class RestApiConstants {
  public static final String ROOT = "root";
  public static final String SLOT = "slot";
  public static final String EPOCH = "epoch";
  public static final String ACTIVE = "active";
  public static final String PAGE_SIZE = "pageSize";
  public static final String PAGE_TOKEN = "pageToken";
  public static final String COMMITTEE_INDEX = "committee_index";
  public static final String RANDAO_REVEAL = "randao_reveal";
  public static final String GRAFFITI = "graffiti";

  public static final String TAG_ADMIN = "Admin";
  public static final String TAG_BEACON = "Beacon";
  public static final String TAG_NODE = "Node";
  public static final String TAG_NETWORK = "Network";
  public static final String TAG_VALIDATOR = "Validator";

  public static final String TAG_V1_NODE = "V1-Node";

  public static final String RES_OK = "200"; // SC_OK
  public static final String RES_ACCEPTED = "202"; // SC_ACCEPTED
  public static final String RES_NO_CONTENT = "204"; // SC_NO_CONTENT
  public static final String RES_BAD_REQUEST = "400"; // SC_BAD_REQUEST
  public static final String RES_NOT_FOUND = "404"; // SC_NOT_FOUND
  public static final String RES_CONFLICT = "406"; // SC_CONFLICT
  public static final String RES_INTERNAL_ERROR = "500"; // SC_INTERNAL_SERVER_ERROR
  public static final String RES_SERVICE_UNAVAILABLE = "503"; // SC_SERVICE_UNAVAILABLE

  public static final String NO_CONTENT_PRE_GENESIS =
      "No content may be returned if the genesis block has not been set, meaning that there is no head to query.";
  public static final String INVALID_BODY_SUPPLIED = "Invalid body supplied.";

  public static final String EPOCH_QUERY_DESCRIPTION = "`UnsignedLong` Epoch number to query.";
  public static final String SLOT_QUERY_DESCRIPTION =
      "`UnsignedLong` Slot to query in the canonical chain.";
  public static final String ROOT_QUERY_DESCRIPTION = "`Bytes32 Hex` Block root to query.";
}
