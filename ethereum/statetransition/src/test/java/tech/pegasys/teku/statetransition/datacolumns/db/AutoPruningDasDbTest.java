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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand;
import tech.pegasys.teku.statetransition.datacolumns.MinCustodyPeriodSlotCalculator;

@SuppressWarnings("FutureReturnValueIgnored")
public class AutoPruningDasDbTest {
  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DasCustodyStand das = DasCustodyStand.builder(spec).build();
  final int custodyPeriodSlots = 10;
  final int custodyPeriodMarginSlots = 2;
  final int prunePeriodSlots = 5;
  final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator =
      slot -> slot.minusMinZero(custodyPeriodSlots);
  AutoPruningDasDb autoPruningDb =
      new AutoPruningDasDb(
          das.db, minCustodyPeriodSlotCalculator, custodyPeriodMarginSlots, prunePeriodSlots);

  private DataColumnSidecar createSidecar(final int slot, final int index) {
    SignedBeaconBlock block = das.createBlockWithBlobs(slot);
    return das.createSidecar(block, index);
  }

  @Test
  void checkOldDataIsAlsoPruned() {
    DataColumnSidecar s0 = createSidecar(0, 0);
    das.db.addSidecar(s0);
    DataColumnSidecar s900 = createSidecar(900, 0);
    das.db.addSidecar(s900);

    DataColumnSidecar s1000 = createSidecar(1000, 0);
    autoPruningDb.addSidecar(s1000);

    assertThat(das.db.getSidecar(DataColumnSlotAndIdentifier.fromDataColumn(s0)).join()).isEmpty();
    assertThat(das.db.getSidecar(DataColumnSlotAndIdentifier.fromDataColumn(s900)).join())
        .isEmpty();
    assertThat(das.db.getSidecar(DataColumnSlotAndIdentifier.fromDataColumn(s1000)).join())
        .isNotEmpty();
  }

  @Test
  void checkPruneIsCalledOncePerPrunePeriod() {
    autoPruningDb.addSidecar(createSidecar(1010, 0));
    long writesCount = das.db.getDbWriteCounter().get();
    autoPruningDb.addSidecar(createSidecar(1010, 1));
    autoPruningDb.addSidecar(createSidecar(990, 0));
    DataColumnSidecar sidecar1000 = createSidecar(1000, 0);
    autoPruningDb.addSidecar(sidecar1000);
    autoPruningDb.addSidecar(createSidecar(1010, 2));
    autoPruningDb.addSidecar(createSidecar(1010 + prunePeriodSlots - 1, 0));
    // check that no additional prune was called
    assertThat(das.db.getDbWriteCounter().get()).isEqualTo(writesCount + 5);
    assertThat(das.db.getSidecar(DataColumnSlotAndIdentifier.fromDataColumn(sidecar1000)).join())
        .isNotEmpty();

    autoPruningDb.addSidecar(createSidecar(1010 + prunePeriodSlots + 1, 0));
    assertThat(das.db.getSidecar(DataColumnSlotAndIdentifier.fromDataColumn(sidecar1000)).join())
        .isEmpty();
  }

  @Test
  void checkPeriodsAreRespected() {
    final int totalPeriod = custodyPeriodSlots + custodyPeriodMarginSlots;
    List<DataColumnSidecar> sidecars =
        IntStream.range(0, 30).mapToObj(slot -> createSidecar(slot, 0)).toList();
    List<DataColumnSlotAndIdentifier> sidecarIds =
        sidecars.stream().map(DataColumnSlotAndIdentifier::fromDataColumn).toList();
    sidecars.forEach(
        sidecar -> {
          autoPruningDb.addSidecar(sidecar);
          int noDataTill =
              sidecar.getSlot().minusMinZero(totalPeriod + prunePeriodSlots).intValue();
          for (int slot = 0; slot < noDataTill; slot++) {
            assertThat(das.db.getSidecar(sidecarIds.get(slot)).join()).isEmpty();
          }
          int existDataFrom = sidecar.getSlot().minusMinZero(totalPeriod).intValue();
          for (int slot = existDataFrom; slot <= sidecar.getSlot().intValue(); slot++) {
            assertThat(das.db.getSidecar(sidecarIds.get(slot)).join()).isNotEmpty();
          }
        });
  }
}
