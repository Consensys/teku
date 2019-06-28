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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomIndexedAttestation;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class AttesterSlashingTest {

  private IndexedAttestation slashableAttestation1 = randomIndexedAttestation();
  private IndexedAttestation slashableAttestation2 = randomIndexedAttestation();

  private AttesterSlashing attesterSlashing =
      new AttesterSlashing(slashableAttestation1, slashableAttestation2);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    AttesterSlashing testAttesterSlashing = attesterSlashing;

    assertEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttesterSlashing testAttesterSlashing =
        new AttesterSlashing(slashableAttestation1, slashableAttestation2);

    assertEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsFalseWhenIndexedAttestation1IsDifferent() {
    // IndexedAttestation is rather involved to create. Just create a random one until it is not
    // the same as the original.
    IndexedAttestation otherIndexedAttestation1 = randomIndexedAttestation();
    while (Objects.equals(otherIndexedAttestation1, slashableAttestation1)) {
      otherIndexedAttestation1 = randomIndexedAttestation();
    }

    AttesterSlashing testAttesterSlashing =
        new AttesterSlashing(otherIndexedAttestation1, slashableAttestation2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsFalseWhenIndexedAttestation2IsDifferent() {
    // IndexedAttestation is rather involved to create. Just create a random one until it is not
    // the ame as the original.
    IndexedAttestation otherIndexedAttestation2 = randomIndexedAttestation();
    while (Objects.equals(otherIndexedAttestation2, slashableAttestation2)) {
      otherIndexedAttestation2 = randomIndexedAttestation();
    }

    AttesterSlashing testAttesterSlashing =
        new AttesterSlashing(slashableAttestation1, otherIndexedAttestation2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttesterSlashingBytes = attesterSlashing.toBytes();
    assertEquals(attesterSlashing, AttesterSlashing.fromBytes(sszAttesterSlashingBytes));
  }
}
