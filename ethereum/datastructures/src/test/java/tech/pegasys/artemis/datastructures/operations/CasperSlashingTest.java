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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomSlashableVoteData;

import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;

class CasperSlashingTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    SlashableVoteData votes1 = randomSlashableVoteData();
    SlashableVoteData votes2 = randomSlashableVoteData();

    CasperSlashing cs1 = new CasperSlashing(votes1, votes2);
    CasperSlashing cs2 = cs1;

    assertEquals(cs1, cs2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    SlashableVoteData votes1 = randomSlashableVoteData();
    SlashableVoteData votes2 = randomSlashableVoteData();

    CasperSlashing cs1 = new CasperSlashing(votes1, votes2);
    CasperSlashing cs2 = new CasperSlashing(votes1, votes2);

    assertEquals(cs1, cs2);
  }

  @Test
  void equalsReturnsFalseWhenVotes1IsDifferent() {
    SlashableVoteData votes1 = randomSlashableVoteData();
    SlashableVoteData votes2 = randomSlashableVoteData();

    // SlashableVoteData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    SlashableVoteData otherVotes1 = randomSlashableVoteData();
    while (Objects.equals(otherVotes1, votes1)) {
      otherVotes1 = randomSlashableVoteData();
    }

    CasperSlashing cs1 = new CasperSlashing(votes1, votes2);
    CasperSlashing cs2 = new CasperSlashing(otherVotes1, votes2);

    assertNotEquals(cs1, cs2);
  }

  @Test
  void equalsReturnsFalseWhenVotes2IsDifferent() {
    SlashableVoteData votes1 = randomSlashableVoteData();
    SlashableVoteData votes2 = randomSlashableVoteData();

    // SlashableVoteData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    SlashableVoteData otherVotes2 = randomSlashableVoteData();
    while (Objects.equals(otherVotes2, votes2)) {
      otherVotes2 = randomSlashableVoteData();
    }

    CasperSlashing cs1 = new CasperSlashing(votes1, votes2);
    CasperSlashing cs2 = new CasperSlashing(votes1, otherVotes2);

    assertNotEquals(cs1, cs2);
  }

  @Test
  void rountripSSZ() {
    CasperSlashing casperSlashing =
        new CasperSlashing(randomSlashableVoteData(), randomSlashableVoteData());
    Bytes sszCasperSlashingBytes = casperSlashing.toBytes();
    assertEquals(casperSlashing, CasperSlashing.fromBytes(sszCasperSlashingBytes));
  }
}
