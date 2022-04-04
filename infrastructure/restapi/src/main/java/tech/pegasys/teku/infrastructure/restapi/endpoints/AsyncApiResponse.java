/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

public class AsyncApiResponse {
  final int responseCode;
  final String responseBody;

  private AsyncApiResponse(final int responseCode, final String responseBody) {
    this.responseCode = responseCode;
    this.responseBody = responseBody;
  }

  public int getResponseCode() {
    return responseCode;
  }

  public boolean hasResponseBody() {
    return !responseBody.isEmpty();
  }

  public String getResponseBody() {
    return responseBody;
  }

  public static AsyncApiResponse respondWithError(
      final int responseCode, final String responseBody) {
    return new AsyncApiResponse(responseCode, responseBody);
  }

  public static AsyncApiResponse respondWithCode(final int responseCode) {
    return new AsyncApiResponse(responseCode, "");
  }

  public static AsyncApiResponse respondOk(final String responseBody) {
    return new AsyncApiResponse(SC_OK, responseBody);
  }
}
