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

package tech.pegasys.teku.validator.api;

import tech.pegasys.teku.infrastructure.async.SafeFuture;

/** Indicates that the request couldn't be completed because the node is currently syncing. */
public class NodeSyncingException extends RuntimeException {

  public static <T> SafeFuture<T> failedFuture() {
    return SafeFuture.failedFuture(new NodeSyncingException());
  }
}
