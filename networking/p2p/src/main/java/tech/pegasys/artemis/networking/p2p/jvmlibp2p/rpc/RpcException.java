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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import org.apache.tuweni.bytes.Bytes;

public class RpcException extends Exception {

  private static final Bytes INVALID_REQUEST_CODE = Bytes.of(1);
  private static final Bytes SERVER_ERROR_CODE = Bytes.of(1);
  public static final RpcException MALFORMED_REQUEST_ERROR =
      new RpcException(INVALID_REQUEST_CODE, "Request was malformed");
  public static final RpcException INCORRECT_LENGTH_ERRROR =
      new RpcException(
          INVALID_REQUEST_CODE, "Specified message length did not match actual length");
  public static final RpcException SERVER_ERROR =
      new RpcException(SERVER_ERROR_CODE, "Unexpected error");
  private final Bytes responseCode;
  private final String errorMessage;

  private RpcException(final Bytes responseCode, final String errorMessage) {
    super(errorMessage);
    this.responseCode = responseCode;
    this.errorMessage = errorMessage;
  }

  public Bytes getResponseCode() {
    return responseCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
