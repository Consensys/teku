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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.statetransition.datacolumns.MinCustodyPeriodSlotCalculator;

public class DataColumnSidecarDbAccessorBuilder {

  // is roughly 600Kb (cache entry for one slot is about 60 bytes)
  private static final int DEFAULT_COLUMN_ID_CACHE_MAX_SLOT_COUNT = 10 * 1024;

  private final DataColumnSidecarDB db;
  private Spec spec;
  private MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;
  private final AutoPruneDbBuilder autoPruneDbBuilder = new AutoPruneDbBuilder();
  private int columnIdCacheMaxSlotCount = DEFAULT_COLUMN_ID_CACHE_MAX_SLOT_COUNT;

  DataColumnSidecarDbAccessorBuilder(DataColumnSidecarDB db) {
    this.db = db;
  }

  public DataColumnSidecarDbAccessorBuilder spec(Spec spec) {
    this.spec = spec;
    return this;
  }

  public DataColumnSidecarDbAccessorBuilder minCustodyPeriodSlotCalculator(
      MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator) {
    this.minCustodyPeriodSlotCalculator = minCustodyPeriodSlotCalculator;
    return this;
  }

  public DataColumnSidecarDbAccessorBuilder columnIdCacheMaxSlotCount(
      int columnIdCacheMaxSlotCount) {
    this.columnIdCacheMaxSlotCount = columnIdCacheMaxSlotCount;
    return this;
  }

  public DataColumnSidecarDbAccessorBuilder withAutoPrune(
      Consumer<AutoPruneDbBuilder> builderConsumer) {
    builderConsumer.accept(this.autoPruneDbBuilder);
    return this;
  }

  private int getNumberOfColumnsForSlot(UInt64 slot) {
    return spec.atSlot(slot)
        .getConfig()
        .toVersionEip7594()
        .map(SpecConfigEip7594::getNumberOfColumns)
        .orElse(0);
  }

  public DataColumnSidecarDbAccessor build() {
    ColumnIdCachingDasDb columnIdCachingDasDb =
        new ColumnIdCachingDasDb(db, this::getNumberOfColumnsForSlot, columnIdCacheMaxSlotCount);
    return autoPruneDbBuilder.build(columnIdCachingDasDb);
  }

  MinCustodyPeriodSlotCalculator getMinCustodyPeriodSlotCalculator() {
    if (minCustodyPeriodSlotCalculator == null) {
      checkNotNull(spec);
      minCustodyPeriodSlotCalculator = MinCustodyPeriodSlotCalculator.createFromSpec(spec);
    }
    return minCustodyPeriodSlotCalculator;
  }

  public class AutoPruneDbBuilder {
    private int pruneMarginSlots = 0;
    private int prunePeriodInSlots = 1;

    /** Additional period in slots to retain data column sidecars in DB before pruning */
    public AutoPruneDbBuilder pruneMarginSlots(int pruneMarginSlots) {
      this.pruneMarginSlots = pruneMarginSlots;
      return this;
    }

    /**
     * Specifies how often (in slots) the db prune will be performed 1 means that the prune is to be
     * called every slot
     */
    public void prunePeriodSlots(int prunePeriodInSlots) {
      this.prunePeriodInSlots = prunePeriodInSlots;
    }

    AutoPruningDasDb build(DataColumnSidecarDB db) {
      return new AutoPruningDasDb(
          db, getMinCustodyPeriodSlotCalculator(), pruneMarginSlots, prunePeriodInSlots);
    }
  }
}
