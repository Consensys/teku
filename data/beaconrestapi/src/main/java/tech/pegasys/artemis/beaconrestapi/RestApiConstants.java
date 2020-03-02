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

package tech.pegasys.artemis.beaconrestapi;

public class RestApiConstants {
  public static final String ROOT = "root";
  public static final String SLOT = "slot";
  public static final String EPOCH = "epoch";
  public static final String ACTIVE = "active";
  public static final String GENESIS = "genesis";

  public static final String TAG_BEACON = "Beacon";
  public static final String TAG_NODE = "Node";
  public static final String TAG_NETWORK = "Network";

  public static final String RES_OK = "200"; // SC_OK
  public static final String RES_NO_CONTENT = "204"; // SC_NO_CONTENT
  public static final String RES_BAD_REQUEST = "400"; // SC_BAD_REQUEST
  public static final String RES_NOT_FOUND = "404"; // SC_NOT_FOUND
  public static final String RES_INTERNAL_ERROR = "500"; // SC_INTERNAL_SERVER_ERROR

  public static final String NO_CONTENT_PRE_GENESIS =
      "No content may be returned if the genesis block has not been set, meaning that there is no head to query.";
}
