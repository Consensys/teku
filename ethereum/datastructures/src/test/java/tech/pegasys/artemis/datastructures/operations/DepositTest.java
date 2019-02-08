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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class DepositTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    List<Bytes32> merkleBranch =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    UnsignedLong merkleTreeIndex = randomUnsignedLong();
    DepositData depositData = randomDepositData();

    Deposit d1 = new Deposit(merkleBranch, merkleTreeIndex, depositData);
    Deposit d2 = d1;

    assertEquals(d1, d2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    List<Bytes32> merkleBranch =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    UnsignedLong merkleTreeIndex = randomUnsignedLong();
    DepositData depositData = randomDepositData();

    Deposit d1 = new Deposit(merkleBranch, merkleTreeIndex, depositData);
    Deposit d2 = new Deposit(merkleBranch, merkleTreeIndex, depositData);

    assertEquals(d1, d2);
  }

  @Test
  void equalsReturnsFalseWhenMerkleBranchesAreDifferent() {
    List<Bytes32> merkleBranch =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    UnsignedLong merkleTreeIndex = randomUnsignedLong();
    DepositData depositData = randomDepositData();

    // Create copy of signature and reverse to ensure it is different.
    List<Bytes32> reversemerkleBranch = new ArrayList<Bytes32>(merkleBranch);
    Collections.reverse(reversemerkleBranch);

    Deposit d1 = new Deposit(merkleBranch, merkleTreeIndex, depositData);
    Deposit d2 = new Deposit(reversemerkleBranch, merkleTreeIndex, depositData);

    assertNotEquals(d1, d2);
  }

  @Test
  void equalsReturnsFalseWhenMerkleTreeIndicesAreDifferent() {
    List<Bytes32> merkleBranch =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    UnsignedLong merkleTreeIndex = randomUnsignedLong();
    DepositData depositData = randomDepositData();

    Deposit d1 = new Deposit(merkleBranch, merkleTreeIndex, depositData);
    Deposit d2 = new Deposit(merkleBranch, merkleTreeIndex.plus(randomUnsignedLong()), depositData);

    assertNotEquals(d1, d2);
  }

  @Test
  void equalsReturnsFalseWhenDepositDataIsDifferent() {
    List<Bytes32> merkleBranch =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    UnsignedLong merkleTreeIndex = randomUnsignedLong();
    DepositData depositData = randomDepositData();

    // DepositData is rather involved to create. Just create a random one until it is not the same
    // as the original.
    DepositData otherDepositData = randomDepositData();
    while (Objects.equals(otherDepositData, depositData)) {
      otherDepositData = randomDepositData();
    }

    Deposit d1 = new Deposit(merkleBranch, merkleTreeIndex, depositData);
    Deposit d2 = new Deposit(merkleBranch, merkleTreeIndex, otherDepositData);

    assertNotEquals(d1, d2);
  }

  @Test
  void rountripSSZ() {
    Deposit deposit =
        new Deposit(
            Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random()),
            randomUnsignedLong(),
            randomDepositData());
    Bytes sszDepositBytes = deposit.toBytes();
    assertEquals(deposit, Deposit.fromBytes(sszDepositBytes));
  }

  private long randomLong() {
    return Math.round(Math.random() * 1000000);
  }

  private UnsignedLong randomUnsignedLong() {
    return UnsignedLong.fromLongBits(randomLong());
  }

  private DepositInput randomDepositInput() {
    return new DepositInput(
        Bytes48.random(), Bytes32.random(), Arrays.asList(Bytes48.random(), Bytes48.random()));
  }

  private DepositData randomDepositData() {
    return new DepositData(randomDepositInput(), randomUnsignedLong(), randomUnsignedLong());
  }
}
