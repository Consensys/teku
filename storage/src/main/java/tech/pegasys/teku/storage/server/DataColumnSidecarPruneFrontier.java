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

package tech.pegasys.teku.storage.server;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * The oldest-first pruning frontier for data column sidecars, owned by the caller (the data column
 * sidecar pruner) and threaded through {@link Database#pruneAllSidecars}.
 *
 * <p>Each component is the next slot a forward prune scan should start from for that sidecar type,
 * or empty before the first prune run (cold start). Keeping the frontier with the caller lets the
 * database stay a stateless data-access layer: each prune starts the scan at the supplied frontier
 * and returns the advanced one, so successive runs never re-scan the deletion tombstones below
 * already-pruned slots.
 */
public record DataColumnSidecarPruneFrontier(
    Optional<UInt64> canonical, Optional<UInt64> nonCanonical) {

  public static final DataColumnSidecarPruneFrontier INITIAL =
      new DataColumnSidecarPruneFrontier(Optional.empty(), Optional.empty());
}
