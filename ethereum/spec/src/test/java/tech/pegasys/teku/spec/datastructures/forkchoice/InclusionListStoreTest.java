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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.operations.SlotAndInclusionListCommitteeRoot;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;

class InclusionListStoreTest {

  private static final UInt64 SLOT = UInt64.valueOf(42);
  private static final UInt64 OTHER_SLOT = UInt64.valueOf(43);
  private static final UInt64 THIRD_SLOT = UInt64.valueOf(44);
  private static final UInt64 VALIDATOR_INDEX = UInt64.valueOf(7);
  private static final UInt64 OTHER_VALIDATOR_INDEX = UInt64.valueOf(8);
  private static final Bytes32 COMMITTEE_ROOT_1 = Bytes32.fromHexStringLenient("0x01");
  private static final Bytes32 COMMITTEE_ROOT_2 = Bytes32.fromHexStringLenient("0x02");
  private static final Bytes32 OTHER_COMMITTEE_ROOT = Bytes32.fromHexStringLenient("0x03");
  private static final Bytes32 THIRD_COMMITTEE_ROOT = Bytes32.fromHexStringLenient("0x04");

  private final InclusionListSchema inclusionListSchema = createInclusionListSchema();

  @Test
  void shouldStoreAndRetrieveInclusionListsByKeyAndSlot() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final InclusionList inclusionList1 =
        createInclusionList(SLOT, VALIDATOR_INDEX, COMMITTEE_ROOT_1);
    final InclusionList inclusionList2 =
        createInclusionList(SLOT, OTHER_VALIDATOR_INDEX, COMMITTEE_ROOT_2);

    inclusionListStore.putInclusionList(inclusionList1);
    inclusionListStore.putInclusionList(inclusionList2);

    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList1)))
        .hasValueSatisfying(lists -> assertThat(lists).containsExactly(inclusionList1));
    assertThat(inclusionListStore.getInclusionLists(SLOT))
        .hasValueSatisfying(
            lists -> assertThat(lists).containsExactly(inclusionList1, inclusionList2));
    assertThat(inclusionListStore.getInclusionLists(OTHER_SLOT))
        .hasValueSatisfying(lists -> assertThat(lists).isEmpty());
  }

  @Test
  void shouldReturnStableSnapshotForExactKeyLookup() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final InclusionList inclusionList1 =
        createInclusionList(SLOT, VALIDATOR_INDEX, COMMITTEE_ROOT_1);
    final InclusionList inclusionList2 =
        createInclusionList(SLOT, OTHER_VALIDATOR_INDEX, COMMITTEE_ROOT_1);

    inclusionListStore.putInclusionList(inclusionList1);
    final List<InclusionList> snapshot =
        inclusionListStore.getInclusionLists(keyFor(inclusionList1)).orElseThrow();

    inclusionListStore.putInclusionList(inclusionList2);

    assertThat(snapshot).containsExactly(inclusionList1);
    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList1)))
        .hasValueSatisfying(
            inclusionLists ->
                assertThat(inclusionLists).containsExactly(inclusionList1, inclusionList2));
  }

  @Test
  void shouldEvictOldestKeyWhenCacheSizeIsExceeded() {
    final InclusionListStore inclusionListStore = new InclusionListStore(2);
    final InclusionList inclusionList1 =
        createInclusionList(SLOT, VALIDATOR_INDEX, COMMITTEE_ROOT_1);
    final InclusionList inclusionList2 =
        createInclusionList(OTHER_SLOT, VALIDATOR_INDEX, COMMITTEE_ROOT_2);
    final InclusionList inclusionList3 =
        createInclusionList(THIRD_SLOT, VALIDATOR_INDEX, THIRD_COMMITTEE_ROOT);

    inclusionListStore.putInclusionList(inclusionList1);
    inclusionListStore.putInclusionList(inclusionList2);
    inclusionListStore.putInclusionList(inclusionList3);

    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList1))).isEmpty();
    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList2)))
        .hasValueSatisfying(lists -> assertThat(lists).containsExactly(inclusionList2));
    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList3)))
        .hasValueSatisfying(lists -> assertThat(lists).containsExactly(inclusionList3));
  }

  @Test
  void shouldTrackEquivocatedInclusionListsByKeyAndValidator() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final InclusionList inclusionList =
        createInclusionList(SLOT, VALIDATOR_INDEX, COMMITTEE_ROOT_1);
    final InclusionList otherKeyInclusionList =
        createInclusionList(SLOT, VALIDATOR_INDEX, OTHER_COMMITTEE_ROOT);

    inclusionListStore.putInclusionList(inclusionList);
    inclusionListStore.putEquivocatedInclusionList(inclusionList);

    assertThat(
            inclusionListStore.isInclusionListEquivocator(
                keyFor(inclusionList), inclusionList.getValidatorIndex()))
        .isTrue();
    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList)))
        .hasValueSatisfying(lists -> assertThat(lists).containsExactly(inclusionList));
    assertThat(inclusionListStore.getInclusionLists(SLOT))
        .hasValueSatisfying(lists -> assertThat(lists).containsExactly(inclusionList));
    assertThat(
            inclusionListStore.isInclusionListEquivocator(
                keyFor(otherKeyInclusionList), otherKeyInclusionList.getValidatorIndex()))
        .isFalse();
  }

  private InclusionList createInclusionList(
      final UInt64 slot, final UInt64 validatorIndex, final Bytes32 committeeRoot) {
    return inclusionListSchema.create(slot, validatorIndex, committeeRoot, List.<Transaction>of());
  }

  private SlotAndInclusionListCommitteeRoot keyFor(final InclusionList inclusionList) {
    return new SlotAndInclusionListCommitteeRoot(
        inclusionList.getSlot(), inclusionList.getInclusionListCommitteeRoot());
  }

  private InclusionListSchema createInclusionListSchema() {
    final SchemaDefinitionsHeze schemaDefinitionsHeze =
        SchemaDefinitionsHeze.required(
            TestSpecFactory.createMinimalHeze().getGenesisSchemaDefinitions());
    return schemaDefinitionsHeze.getInclusionListSchema();
  }
}
