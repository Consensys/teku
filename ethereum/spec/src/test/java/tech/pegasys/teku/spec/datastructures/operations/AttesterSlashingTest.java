/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing.AttesterSlashingSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true)
class AttesterSlashingTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private AttesterSlashingSchema attesterSlashingSchema;
  private IndexedAttestation indexedAttestation1;
  private IndexedAttestation indexedAttestation2;
  private IndexedAttestation otherIndexedAttestation1;
  private IndexedAttestation otherIndexedAttestation2;
  private AttesterSlashing attesterSlashing;

  @BeforeEach
  public void setup(TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    attesterSlashingSchema = spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema();
    indexedAttestation1 = dataStructureUtil.randomIndexedAttestation();
    indexedAttestation2 = dataStructureUtil.randomIndexedAttestation();
    otherIndexedAttestation1 = dataStructureUtil.randomIndexedAttestation();
    // IndexedAttestation is rather involved to create. Just create a random one until it is not
    // the same as the original.
    while (Objects.equals(otherIndexedAttestation1, indexedAttestation1)) {
      otherIndexedAttestation1 = dataStructureUtil.randomIndexedAttestation();
    }
    otherIndexedAttestation2 = dataStructureUtil.randomIndexedAttestation();
    // IndexedAttestation is rather involved to create. Just create a random one until it is not
    // the ame as the original.
    while (Objects.equals(otherIndexedAttestation2, indexedAttestation2)) {
      otherIndexedAttestation2 = dataStructureUtil.randomIndexedAttestation();
    }

    attesterSlashing = attesterSlashingSchema.create(indexedAttestation1, indexedAttestation2);
  }

  @TestTemplate
  void equalsReturnsTrueWhenObjectsAreSame() {
    AttesterSlashing testAttesterSlashing = attesterSlashing;

    assertEquals(attesterSlashing, testAttesterSlashing);
  }

  @TestTemplate
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttesterSlashing testAttesterSlashing =
        attesterSlashingSchema.create(indexedAttestation1, indexedAttestation2);

    assertEquals(attesterSlashing, testAttesterSlashing);
  }

  @TestTemplate
  void equalsReturnsFalseWhenIndexedAttestation1IsDifferent() {
    AttesterSlashing testAttesterSlashing =
        attesterSlashingSchema.create(otherIndexedAttestation1, indexedAttestation2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @TestTemplate
  void equalsReturnsFalseWhenIndexedAttestation2IsDifferent() {
    AttesterSlashing testAttesterSlashing =
        attesterSlashingSchema.create(indexedAttestation1, otherIndexedAttestation2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @TestTemplate
  void roundtripSsz() {
    AttesterSlashing newAttesterSlashing =
        attesterSlashingSchema.sszDeserialize(attesterSlashing.sszSerialize());
    assertEquals(attesterSlashing, newAttesterSlashing);
  }
}
