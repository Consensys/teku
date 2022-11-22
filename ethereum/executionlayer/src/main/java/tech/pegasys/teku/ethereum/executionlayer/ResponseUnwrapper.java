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

package tech.pegasys.teku.ethereum.executionlayer;

import tech.pegasys.teku.ethereum.executionclient.schema.Response;

class ResponseUnwrapper {

  static <K> K unwrapExecutionClientResponseOrThrow(final Response<K> response) {
    return unwrapResponseOrThrow(ExecutionType.EXECUTION_CLIENT, response);
  }

  static <K> K unwrapBuilderResponseOrThrow(final Response<K> response) {
    return unwrapResponseOrThrow(ExecutionType.BUILDER, response);
  }

  static <K> K unwrapResponseOrThrow(
      final ExecutionType executionType, final Response<K> response) {
    if (response.isFailure()) {
      final String errorMessage =
          String.format(
              "Invalid remote response from the %s: %s", executionType, response.getErrorMessage());
      throw new InvalidRemoteResponseException(errorMessage);
    }
    return response.getPayload();
  }
}
