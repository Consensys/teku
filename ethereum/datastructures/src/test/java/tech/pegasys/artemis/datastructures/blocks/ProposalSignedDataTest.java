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
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class ProposalSignedDataTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 blockHash = Bytes32.random();

    ProposalSignedData psd1 = new ProposalSignedData(slot, shard, blockHash);
    ProposalSignedData psd2 = psd1;

    assertEquals(psd1, psd2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 blockHash = Bytes32.random();

    ProposalSignedData psd1 = new ProposalSignedData(slot, shard, blockHash);
    ProposalSignedData psd2 = new ProposalSignedData(slot, shard, blockHash);

    assertEquals(psd1, psd2);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 blockHash = Bytes32.random();

    ProposalSignedData psd1 = new ProposalSignedData(slot, shard, blockHash);
    ProposalSignedData psd2 =
        new ProposalSignedData(slot.plus(randomUnsignedLong()), shard, blockHash);

    assertNotEquals(psd1, psd2);
  }

  @Test
  void equalsReturnsFalseWhenShardsAreDifferent() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 blockHash = Bytes32.random();

    ProposalSignedData psd1 = new ProposalSignedData(slot, shard, blockHash);
    ProposalSignedData psd2 =
        new ProposalSignedData(slot, shard.plus(randomUnsignedLong()), blockHash);

    assertNotEquals(psd1, psd2);
  }

  @Test
  void equalsReturnsFalseWhenBlockHashesAreDifferent() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong shard = randomUnsignedLong();
    Bytes32 blockHash = Bytes32.random();

    ProposalSignedData psd1 = new ProposalSignedData(slot, shard, blockHash);
    ProposalSignedData psd2 = new ProposalSignedData(slot, shard, blockHash.not());

    assertNotEquals(psd1, psd2);
  }

  @Test
  void rountripSSZ() {
    ProposalSignedData proposalSignedData =
        new ProposalSignedData(randomUnsignedLong(), randomUnsignedLong(), Bytes32.random());
    Bytes sszProposalSignedDataBytes = proposalSignedData.toBytes();
    assertEquals(proposalSignedData, ProposalSignedData.fromBytes(sszProposalSignedDataBytes));
  }

  private long randomLong() {
    return Math.round(Math.random() * 1000000);
  }

  private UnsignedLong randomUnsignedLong() {
    return UnsignedLong.fromLongBits(randomLong());
  }
}
