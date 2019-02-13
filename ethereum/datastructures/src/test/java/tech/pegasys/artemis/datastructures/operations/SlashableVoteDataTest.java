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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomAttestationData;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class SlashableVoteDataTest {

  List<Integer> custodyBit0Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
  List<Integer> custodyBit1Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
  AttestationData data = randomAttestationData();
  BLSSignature aggregateSignature = new BLSSignature(Bytes48.random(), Bytes48.random());

  SlashableVoteData slashableVoteData =
      new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    SlashableVoteData testSlashableVoteData = slashableVoteData;

    assertEquals(slashableVoteData, testSlashableVoteData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    SlashableVoteData testSlashableVoteData =
        new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);

    assertEquals(slashableVoteData, testSlashableVoteData);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBit0IndicesAreDifferent() {
    // Create copy of custodyBit0Indices and reverse to ensure it is different.
    List<Integer> reverseCustodyBit0Indices = new ArrayList<Integer>(custodyBit0Indices);
    Collections.reverse(reverseCustodyBit0Indices);

    SlashableVoteData testSlashableVoteData =
        new SlashableVoteData(
            reverseCustodyBit0Indices, custodyBit1Indices, data, aggregateSignature);

    assertNotEquals(slashableVoteData, testSlashableVoteData);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBit1IndicesAreDifferent() {
    // Create copy of custodyBit1Indices and reverse to ensure it is different.
    List<Integer> reverseCustodyBit1Indices = new ArrayList<Integer>(custodyBit1Indices);
    Collections.reverse(reverseCustodyBit1Indices);

    SlashableVoteData testSlashableVoteData =
        new SlashableVoteData(
            custodyBit0Indices, reverseCustodyBit1Indices, data, aggregateSignature);

    assertNotEquals(slashableVoteData, testSlashableVoteData);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    AttestationData otherData = randomAttestationData();
    while (Objects.equals(otherData, data)) {
      otherData = randomAttestationData();
    }

    SlashableVoteData testSlashableVoteData =
        new SlashableVoteData(
            custodyBit0Indices, custodyBit1Indices, otherData, aggregateSignature);

    assertNotEquals(slashableVoteData, testSlashableVoteData);
  }

  @Test
  void equalsReturnsFalseWhenAggregrateSignaturesAreDifferent() {
    // Create copy of custodyBit1Indices and reverse to ensure it is different.
    BLSSignature reverseAggregateSignature =
        new BLSSignature(aggregateSignature.getC1(), aggregateSignature.getC0());

    SlashableVoteData testSlashableVoteData =
        new SlashableVoteData(
            custodyBit0Indices, custodyBit1Indices, data, reverseAggregateSignature);

    assertNotEquals(slashableVoteData, testSlashableVoteData);
  }

  @Test
  void rountripSSZ() {
    Bytes sszSlashableVoteDataBytes = slashableVoteData.toBytes();
    assertEquals(slashableVoteData, SlashableVoteData.fromBytes(sszSlashableVoteDataBytes));
  }
}
