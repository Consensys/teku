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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDepositData;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class DepositTest {

  private List<Bytes32> merkleBranch =
      Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
  private UnsignedLong merkleTreeIndex = randomUnsignedLong();
  private DepositData depositData = randomDepositData();

  private Deposit deposit = new Deposit(merkleBranch, merkleTreeIndex, depositData);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Deposit testDeposit = deposit;

    assertEquals(deposit, testDeposit);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Deposit testDeposit = new Deposit(merkleBranch, merkleTreeIndex, depositData);

    assertEquals(deposit, testDeposit);
  }

  @Test
  void equalsReturnsFalseWhenMerkleBranchesAreDifferent() {
    // Create copy of signature and reverse to ensure it is different.
    List<Bytes32> reversemerkleBranch = new ArrayList<Bytes32>(merkleBranch);
    Collections.reverse(reversemerkleBranch);

    Deposit testDeposit = new Deposit(reversemerkleBranch, merkleTreeIndex, depositData);

    assertNotEquals(deposit, testDeposit);
  }

  @Test
  void equalsReturnsFalseWhenMerkleTreeIndicesAreDifferent() {
    Deposit testDeposit =
        new Deposit(merkleBranch, merkleTreeIndex.plus(randomUnsignedLong()), depositData);

    assertNotEquals(deposit, testDeposit);
  }

  @Test
  void equalsReturnsFalseWhenDepositDataIsDifferent() {
    // DepositData is rather involved to create. Just create a random one until it is not the same
    // as the original.
    DepositData otherDepositData = randomDepositData();
    while (Objects.equals(otherDepositData, depositData)) {
      otherDepositData = randomDepositData();
    }

    Deposit testDeposit = new Deposit(merkleBranch, merkleTreeIndex, otherDepositData);

    assertNotEquals(deposit, testDeposit);
  }

  @Test
  void rountripSSZ() {
    Bytes sszDepositBytes = deposit.toBytes();
    assertEquals(deposit, Deposit.fromBytes(sszDepositBytes));
  }
}
