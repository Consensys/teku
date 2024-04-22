/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.validation;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.electra.DataColumnSidecar;

public interface DataColumnSidecarGossipValidator {

  public static DataColumnSidecarGossipValidator NOOP =
      dataColumnSidecar -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT);

  public static DataColumnSidecarGossipValidator create(DataColumnSidecarValidator validator) {
    return dataColumnSidecar ->
        validator
            .validate(dataColumnSidecar)
            .handle(
                (__, err) ->
                    err == null
                        ? InternalValidationResult.ACCEPT
                        : InternalValidationResult.reject("Error: %s", err));
  }

  SafeFuture<InternalValidationResult> validate(DataColumnSidecar dataColumnSidecar);
}
