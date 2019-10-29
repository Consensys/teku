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

package org.ethereum.beacon.ssz.visitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.ethereum.beacon.crypto.Hashes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class SSZHasherMerkleizerTest {

  @Test
  public void testNextBinaryLog() {
    SSZSimpleHasher hasher = new SSZSimpleHasher(null, Hashes::sha256, 32);
    assertEquals(0, hasher.nextBinaryLog(0));
    assertEquals(0, hasher.nextBinaryLog(1));
    assertEquals(3, hasher.nextBinaryLog(7));
    assertEquals(3, hasher.nextBinaryLog(8));
    assertEquals(4, hasher.nextBinaryLog(9));
  }

  @Test
  public void testMerkleizeEqual() {
    SSZSimpleHasher hasher = new SSZSimpleHasher(null, Hashes::sha256, 32);

    List<BytesValue> input = new ArrayList<>();
    input.add(Hashes.sha256(BytesValue.fromHexString("aa")));
    input.add(Hashes.sha256(BytesValue.fromHexString("bb")));
    input.add(Hashes.sha256(BytesValue.fromHexString("cc")));
    MerkleTrie base1 = hasher.merkleize(input, 8);
    MerkleTrie virtual1 = hasher.merkleize(input, 4, 8);
    MerkleTrie base2 = hasher.merkleize(input, 16);
    MerkleTrie virtual2 = hasher.merkleize(input, 4, 16);
    MerkleTrie base3 = hasher.merkleize(input, 32);
    MerkleTrie virtual3 = hasher.merkleize(input, 4, 32);
    assertEquals(base1.getPureRoot(), virtual1.getPureRoot());
    assertEquals(base2.getPureRoot(), virtual2.getPureRoot());
    assertEquals(base3.getPureRoot(), virtual3.getPureRoot());
  }

  @Test
  public void testMerkleizeEmptyEqual() {
    SSZSimpleHasher hasher = new SSZSimpleHasher(null, Hashes::sha256, 32);

    List<BytesValue> input = new ArrayList<>();
    MerkleTrie base1 = hasher.merkleize(input, 8);
    MerkleTrie virtual1 = hasher.merkleize(input, 1, 8);
    MerkleTrie base2 = hasher.merkleize(input, 16);
    MerkleTrie virtual2 = hasher.merkleize(input, 1, 16);
    MerkleTrie base3 = hasher.merkleize(input, 32);
    MerkleTrie virtual3 = hasher.merkleize(input, 1, 32);
    assertEquals(base1.getPureRoot(), virtual1.getPureRoot());
    assertEquals(base2.getPureRoot(), virtual2.getPureRoot());
    assertEquals(base3.getPureRoot(), virtual3.getPureRoot());
  }
}
