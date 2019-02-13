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

  private SlashableAttestation votes1 = randomSlashableAttestation();
  private SlashableAttestation votes2 = randomSlashableAttestation();

  private AttesterSlashing attesterSlashing = new AttesterSlashing(votes1, votes2);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    AttesterSlashing testAttesterSlashing = attesterSlashing;

    assertEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttesterSlashing testAttesterSlashing = new AttesterSlashing(votes1, votes2);

    assertEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsFalseWhenVotes1IsDifferent() {
    // SlashableAttestation is rather involved to create. Just create a random one until it is not
    // the same as the original.
    SlashableAttestation otherVotes1 = randomSlashableAttestation();
    while (Objects.equals(otherVotes1, votes1)) {
      otherVotes1 = randomSlashableAttestation();
    }

    AttesterSlashing testAttesterSlashing = new AttesterSlashing(otherVotes1, votes2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void equalsReturnsFalseWhenVotes2IsDifferent() {
    // SlashableAttestation is rather involved to create. Just create a random one until it is not
    // the ame as the original.
    SlashableAttestation otherVotes2 = randomSlashableAttestation();
    while (Objects.equals(otherVotes2, votes2)) {
      otherVotes2 = randomSlashableAttestation();
    }

    AttesterSlashing testAttesterSlashing = new AttesterSlashing(votes1, otherVotes2);

    assertNotEquals(attesterSlashing, testAttesterSlashing);
  }

  @Test
  void rountripSSZ() {
    Bytes sszCasperSlashingBytes = attesterSlashing.toBytes();
    assertEquals(attesterSlashing, AttesterSlashing.fromBytes(sszCasperSlashingBytes));
  }
}
