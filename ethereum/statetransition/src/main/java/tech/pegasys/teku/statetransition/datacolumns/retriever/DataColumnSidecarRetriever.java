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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.ColumnSlotAndIdentifier;

/** The class which searches for a specific {@link DataColumnSidecar} across nodes in the network */
public interface DataColumnSidecarRetriever {

  /**
   * Queues the specified sidecar for search
   *
   * @return a future which may run indefinitely until finds a requested data or cancelled
   */
  SafeFuture<DataColumnSidecar> retrieve(ColumnSlotAndIdentifier columnId);
}
