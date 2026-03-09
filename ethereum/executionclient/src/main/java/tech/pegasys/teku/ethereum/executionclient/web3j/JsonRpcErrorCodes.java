/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

enum JsonRpcErrorCodes {
  PARSE_ERROR(-32700, "Parse error"),
  INVALID_REQUEST(-32600, "Invalid Request"),
  METHOD_NOT_FOUND(-32601, "Method not found"),
  INVALID_PARAMS(-32602, "Invalid params"),
  INTERNAL_ERROR(-32603, "Internal error"),
  SERVER_ERROR(-32000, "Server error");

  private final int errorCode;
  private final String description;
  private static final Int2ObjectOpenHashMap<JsonRpcErrorCodes> CODE_TO_ERROR_MAP;

  static {
    CODE_TO_ERROR_MAP = new Int2ObjectOpenHashMap<>();
    for (final JsonRpcErrorCodes error : values()) {
      CODE_TO_ERROR_MAP.put(error.getErrorCode(), error);
    }
  }

  JsonRpcErrorCodes(final int errorCode, final String description) {
    this.errorCode = errorCode;
    this.description = description;
  }

  public int getErrorCode() {
    return errorCode;
  }

  public String getDescription() {
    return description;
  }

  public static String getDescription(final int errorCode) {
    return fromCode(errorCode).getDescription();
  }

  public static JsonRpcErrorCodes fromCode(final int errorCode) {
    final JsonRpcErrorCodes error = CODE_TO_ERROR_MAP.get(errorCode);
    if (error != null) {
      return error;
    }
    return errorCode >= -32099 && errorCode <= -32000 ? SERVER_ERROR : INTERNAL_ERROR;
  }
}
