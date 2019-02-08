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

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    List<Integer> custodyBit0Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    List<Integer> custodyBit1Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    AttestationData data = randomAttestationData();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    SlashableVoteData svd1 =
        new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);
    SlashableVoteData svd2 = svd1;

    assertEquals(svd1, svd2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    List<Integer> custodyBit0Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    List<Integer> custodyBit1Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    AttestationData data = randomAttestationData();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    SlashableVoteData svd1 =
        new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);
    SlashableVoteData svd2 =
        new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);

    assertEquals(svd1, svd2);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBit0IndicesAreDifferent() {
    List<Integer> custodyBit0Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    List<Integer> custodyBit1Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    AttestationData data = randomAttestationData();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    // Create copy of custodyBit0Indices and reverse to ensure it is different.
    List<Integer> reverseCustodyBit0Indices = new ArrayList<Integer>(custodyBit0Indices);
    Collections.reverse(reverseCustodyBit0Indices);

    SlashableVoteData svd1 =
        new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);
    SlashableVoteData svd2 =
        new SlashableVoteData(
            reverseCustodyBit0Indices, custodyBit1Indices, data, aggregateSignature);

    assertNotEquals(svd1, svd2);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBit1IndicesAreDifferent() {
    List<Integer> custodyBit0Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    List<Integer> custodyBit1Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    AttestationData data = randomAttestationData();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    // Create copy of custodyBit1Indices and reverse to ensure it is different.
    List<Integer> reverseCustodyBit1Indices = new ArrayList<Integer>(custodyBit1Indices);
    Collections.reverse(reverseCustodyBit1Indices);

    SlashableVoteData svd1 =
        new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);
    SlashableVoteData svd2 =
        new SlashableVoteData(
            custodyBit0Indices, reverseCustodyBit1Indices, data, aggregateSignature);

    assertNotEquals(svd1, svd2);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    List<Integer> custodyBit0Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    List<Integer> custodyBit1Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    AttestationData data = randomAttestationData();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    AttestationData otherData = randomAttestationData();
    while (Objects.equals(otherData, data)) {
      otherData = randomAttestationData();
    }

    SlashableVoteData svd1 =
        new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);
    SlashableVoteData svd2 =
        new SlashableVoteData(
            custodyBit0Indices, custodyBit1Indices, otherData, aggregateSignature);

    assertNotEquals(svd1, svd2);
  }

  @Test
  void equalsReturnsFalseWhenAggregrateSignaturesAreDifferent() {
    List<Integer> custodyBit0Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    List<Integer> custodyBit1Indices = Arrays.asList(randomInt(), randomInt(), randomInt());
    AttestationData data = randomAttestationData();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    // Create copy of custodyBit1Indices and reverse to ensure it is different.
    List<Bytes48> reverseAggregateSignature = new ArrayList<Bytes48>(aggregateSignature);
    Collections.reverse(reverseAggregateSignature);

    SlashableVoteData svd1 =
        new SlashableVoteData(custodyBit0Indices, custodyBit1Indices, data, aggregateSignature);
    SlashableVoteData svd2 =
        new SlashableVoteData(
            custodyBit0Indices, custodyBit1Indices, data, reverseAggregateSignature);

    assertNotEquals(svd1, svd2);
  }

  @Test
  void rountripSSZ() {
    SlashableVoteData slashableVoteData =
        new SlashableVoteData(
            Arrays.asList(randomInt(), randomInt(), randomInt()),
            Arrays.asList(randomInt(), randomInt(), randomInt()),
            randomAttestationData(),
            Arrays.asList(Bytes48.random(), Bytes48.random()));
    Bytes sszSlashableVoteDataBytes = slashableVoteData.toBytes();
    assertEquals(slashableVoteData, SlashableVoteData.fromBytes(sszSlashableVoteDataBytes));
  }
}
