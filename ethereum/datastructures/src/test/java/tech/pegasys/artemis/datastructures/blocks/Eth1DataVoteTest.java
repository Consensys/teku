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

package tech.pegasys.artemis.datastructures.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

class Eth1DataVoteTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private Eth1Data eth1Data = dataStructureUtil.randomEth1Data();
  private UnsignedLong voteCount = dataStructureUtil.randomUnsignedLong();

  private Eth1DataVote eth1DataVote = new Eth1DataVote(eth1Data, voteCount);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Eth1DataVote testEth1DataVote = eth1DataVote;

    assertEquals(eth1DataVote, testEth1DataVote);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Eth1DataVote testEth1DataVote = new Eth1DataVote(eth1Data, voteCount);

    assertEquals(eth1DataVote, testEth1DataVote);
  }

  @Test
  void equalsReturnsFalseWhenEth1DataIsDifferent() {
    // Eth1Data is rather involved to create. Just create a random one until it is not the same
    // as the original.
    Eth1Data otherEth1Data = dataStructureUtil.randomEth1Data();
    while (Objects.equals(otherEth1Data, eth1Data)) {
      otherEth1Data = dataStructureUtil.randomEth1Data();
    }
    Eth1DataVote testEth1DataVote = new Eth1DataVote(otherEth1Data, voteCount);

    assertNotEquals(eth1DataVote, testEth1DataVote);
  }

  @Test
  void equalsReturnsFalseWhenVoteCountsAreDifferent() {
    Eth1DataVote testEth1DataVote =
        new Eth1DataVote(eth1Data, voteCount.plus(dataStructureUtil.randomUnsignedLong()));

    assertNotEquals(eth1DataVote, testEth1DataVote);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszEth1DataVoteBytes = SimpleOffsetSerializer.serialize(eth1DataVote);
    assertEquals(
        eth1DataVote, SimpleOffsetSerializer.deserialize(sszEth1DataVoteBytes, Eth1DataVote.class));
  }
}
