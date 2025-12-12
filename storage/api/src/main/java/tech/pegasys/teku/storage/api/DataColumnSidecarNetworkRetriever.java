/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.api;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;

public interface DataColumnSidecarNetworkRetriever {
  DataColumnSidecarNetworkRetriever DISABLED =
      new DataColumnSidecarNetworkRetriever() {
        @Override
        public SafeFuture<List<DataColumnSidecar>> retrieveDataColumnSidecars(
            List<DataColumnSlotAndIdentifier> requiredIdentifiers) {
          return SafeFuture.failedFuture(
              new UnsupportedOperationException(
                  "Attempting to retrieve column sidecar when disabled"));
        }

        @Override
        public boolean isEnabled() {
          return false;
        }
      };

  static DataColumnSidecarNetworkRetriever create(
      final Function<DataColumnSlotAndIdentifier, SafeFuture<DataColumnSidecar>> retriever,
      final Runnable retrieverFlusher,
      final Function<DataColumnSidecar, SafeFuture<Void>> dbWriter,
      final Duration retrievalTimeout) {
    return new DataColumnSidecarNetworkRetrieverImpl(
        retriever, retrieverFlusher, dbWriter, retrievalTimeout);
  }

  SafeFuture<List<DataColumnSidecar>> retrieveDataColumnSidecars(
      List<DataColumnSlotAndIdentifier> requiredIdentifiers);

  default boolean isEnabled() {
    return true;
  }
}
