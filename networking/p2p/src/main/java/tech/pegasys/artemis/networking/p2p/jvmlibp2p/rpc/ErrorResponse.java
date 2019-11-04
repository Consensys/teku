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

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

public class ErrorResponse {

  private static final Bytes INVALID_REQUEST_CODE = Bytes.of(1);
  private static final Bytes SERVER_ERROR_CODE = Bytes.of(1);
  public static final ErrorResponse MALFORMED_REQUEST_ERROR =
      new ErrorResponse(INVALID_REQUEST_CODE, "Request was malformed");
  public static final ErrorResponse INCORRECT_LENGTH_ERRROR =
      new ErrorResponse(
          INVALID_REQUEST_CODE, "Specified message length did not match actual length");
  public static final ErrorResponse SERVER_ERROR =
      new ErrorResponse(SERVER_ERROR_CODE, "Unexpected error");
  private final Bytes responseCode;
  private final String errorMessage;

  private ErrorResponse(final Bytes responseCode, final String errorMessage) {
    this.responseCode = responseCode;
    this.errorMessage = errorMessage;
  }

  public Bytes getResponseCode() {
    return responseCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("responseCode", responseCode)
        .add("errorMessage", errorMessage)
        .toString();
  }
}
