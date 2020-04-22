/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

class AttesterSlashingTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private IndexedAttestation indexedAttestation1 = dataStructureUtil.randomIndexedAttestation();
  private IndexedAttestation indexedAttestation2 = dataStructureUtil.randomIndexedAttestation();

  private AttesterSlashing attesterSlashing =
      new AttesterSlashing(indexedAttestation1, indexedAttestation2);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    AttesterSlashing testAttesterSlashing = attesterSlashing;

    assertEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttesterSlashing testAttesterSlashing =
        new AttesterSlashing(indexedAttestation1, indexedAttestation2);

    assertEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsFalseWhenIndexedAttestation1IsDifferent() {
    // IndexedAttestation is rather involved to create. Just create a random one until it is not
    // the same as the original.
    IndexedAttestation otherIndexedAttestation1 = dataStructureUtil.randomIndexedAttestation();
    while (Objects.equals(otherIndexedAttestation1, indexedAttestation1)) {
      otherIndexedAttestation1 = dataStructureUtil.randomIndexedAttestation();
    }

    AttesterSlashing testAttesterSlashing =
        new AttesterSlashing(otherIndexedAttestation1, indexedAttestation2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsFalseWhenIndexedAttestation2IsDifferent() {
    // IndexedAttestation is rather involved to create. Just create a random one until it is not
    // the ame as the original.
    IndexedAttestation otherIndexedAttestation2 = dataStructureUtil.randomIndexedAttestation();
    while (Objects.equals(otherIndexedAttestation2, indexedAttestation2)) {
      otherIndexedAttestation2 = dataStructureUtil.randomIndexedAttestation();
    }

    AttesterSlashing testAttesterSlashing =
        new AttesterSlashing(indexedAttestation1, otherIndexedAttestation2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void roundtripSsz() {
    AttesterSlashing attesterSlashing = dataStructureUtil.randomAttesterSlashing();
    AttesterSlashing newAttesterSlashing =
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(attesterSlashing), AttesterSlashing.class);
    assertEquals(attesterSlashing, newAttesterSlashing);
  }
}
