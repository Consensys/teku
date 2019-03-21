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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomSlashableAttestation;

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;

class AttesterSlashingTest {

  private SlashableAttestation slashableAttestation1 = randomSlashableAttestation();
  private SlashableAttestation slashableAttestation2 = randomSlashableAttestation();

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
  void equalsReturnsFalseWhenSlashableAttestation1IsDifferent() {
    // SlashableAttestation is rather involved to create. Just create a random one until it is not
    // the same as the original.
    SlashableAttestation otherSlashableAttestation1 = randomSlashableAttestation();
    while (Objects.equals(otherSlashableAttestation1, slashableAttestation1)) {
      otherSlashableAttestation1 = randomSlashableAttestation();
    }

    AttesterSlashing testAttesterSlashing =
        new AttesterSlashing(otherSlashableAttestation1, slashableAttestation2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsFalseWhenSlashableAttestation2IsDifferent() {
    // SlashableAttestation is rather involved to create. Just create a random one until it is not
    // the ame as the original.
    SlashableAttestation otherSlashableAttestation2 = randomSlashableAttestation();
    while (Objects.equals(otherSlashableAttestation2, slashableAttestation2)) {
      otherSlashableAttestation2 = randomSlashableAttestation();
    }

    AttesterSlashing testAttesterSlashing =
        new AttesterSlashing(slashableAttestation1, otherSlashableAttestation2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttesterSlashingBytes = attesterSlashing.toBytes();
    assertEquals(attesterSlashing, AttesterSlashing.fromBytes(sszAttesterSlashingBytes));
  }
}
