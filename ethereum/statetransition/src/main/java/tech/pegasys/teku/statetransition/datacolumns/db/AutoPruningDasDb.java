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

package tech.pegasys.teku.statetransition.datacolumns.db;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.MinCustodyPeriodSlotCalculator;

class AutoPruningDasDb extends AbstractDelegatingDasDb implements DataColumnSidecarDbAccessor {

  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;
  private final DataColumnSidecarDB delegate;
  private final int marginPruneSlots;
  private final int prunePeriod;

  private volatile UInt64 nextPruneSlot = UInt64.ZERO;

  public AutoPruningDasDb(
      final DataColumnSidecarDB delegate,
      final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator,
      final int marginPruneSlots,
      final int prunePeriod) {
    super(delegate);
    this.delegate = checkNotNull(delegate);
    this.minCustodyPeriodSlotCalculator = checkNotNull(minCustodyPeriodSlotCalculator);
    this.marginPruneSlots = marginPruneSlots;
    checkArgument(prunePeriod >= 1, "prunePeriod should be >= 1");
    this.prunePeriod = prunePeriod;
  }

  private UInt64 calculatePruneSlot(final UInt64 currentSlot) {
    return minCustodyPeriodSlotCalculator
        .getMinCustodyPeriodSlot(currentSlot)
        .minusMinZero(marginPruneSlots);
  }

  @Override
  public SafeFuture<Void> addSidecar(final DataColumnSidecar sidecar) {
    final SafeFuture<Void> addFuture = super.addSidecar(sidecar);
    final SafeFuture<Void> pruneFuture;
    if (sidecar.getSlot().isGreaterThanOrEqualTo(nextPruneSlot)) {
      nextPruneSlot = sidecar.getSlot().plus(prunePeriod);
      final UInt64 minCustodySlot = calculatePruneSlot(sidecar.getSlot());
      pruneFuture = delegate.pruneAllSidecars(minCustodySlot);
    } else {
      pruneFuture = SafeFuture.COMPLETE;
    }
    return SafeFuture.allOf(addFuture, pruneFuture);
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstCustodyIncompleteSlot() {
    return delegate.getFirstCustodyIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<UInt64>> getFirstSamplerIncompleteSlot() {
    return delegate.getFirstSamplerIncompleteSlot();
  }

  @Override
  public SafeFuture<Void> setFirstCustodyIncompleteSlot(final UInt64 slot) {
    return delegate.setFirstCustodyIncompleteSlot(slot);
  }

  @Override
  public SafeFuture<Void> setFirstSamplerIncompleteSlot(final UInt64 slot) {
    return delegate.setFirstSamplerIncompleteSlot(slot);
  }
}
