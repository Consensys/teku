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

package org.ethereum.beacon.pow;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.crypto.Hashes;
import org.junit.Test;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.uint.UInt64;

public class DepositDataMerkleTest {

  @Test
  public void test() {
    MerkleTree<DepositData> simple = new DepositSimpleMerkle(Hashes::sha256, 32);
    MerkleTree<DepositData> incremental = new DepositBufferedMerkle(Hashes::sha256, 32, 3);
    Consumer<Integer> insertInBoth =
        integer -> {
          simple.addValue(createDepositData(integer));
          incremental.addValue(createDepositData(integer));
        };
    for (int i = 1; i < 20; ++i) {
      System.out.println(i);
      insertInBoth.accept(i);
      assertEquals(simple.getRoot(i - 1), incremental.getRoot(i - 1));
    }
    int someIndex = simple.getLastIndex() + 1;
    for (int i = 20; i < 40; ++i) {
      insertInBoth.accept(i);
      assertArrayEquals(
          simple.getProof(someIndex, i).toArray(), incremental.getProof(someIndex, i).toArray());
    }
  }

  @Test
  public void sszComparisonCheck() {
    ObjectHasher<Hash32> sszHasher =
        ObjectHasher.createSSZOverSHA256(BeaconChainSpec.DEFAULT_CONSTANTS);

    List<DepositData> sourceList =
        IntStream.range(0, 2).mapToObj(this::createDepositData).collect(Collectors.toList());
    ReadList<Integer, DepositData> twoElementList = ReadList.wrap(sourceList, Integer::new, 2);

    MerkleTree<DepositData> oneLevelTree = new DepositBufferedMerkle(Hashes::sha256, 1, 2);
    sourceList.forEach(oneLevelTree::addValue);

    assertEquals(sszHasher.getHash(twoElementList), oneLevelTree.getRoot(1));

    ReadList<Integer, DepositData> fourElementList = ReadList.wrap(sourceList, Integer::new, 4);
    MerkleTree<DepositData> twoLevelTree = new DepositBufferedMerkle(Hashes::sha256, 2, 4);
    sourceList.forEach(twoLevelTree::addValue);

    assertEquals(sszHasher.getHash(fourElementList), twoLevelTree.getRoot(1));
  }

  @Test
  public void validProofIsGenerated() {
    Function<BytesValue, Hash32> hash = Hashes::sha256;
    int treeDepth = 2;

    List<DepositData> sourceList =
        IntStream.range(0, 2).mapToObj(this::createDepositData).collect(Collectors.toList());
    DepositBufferedMerkle twoLevelTree = new DepositBufferedMerkle(hash, treeDepth, 4);
    sourceList.forEach(twoLevelTree::addValue);

    List<Hash32> hashes =
        sourceList.stream()
            .map(data -> DepositDataMerkle.createDepositDataValue(data, hash))
            .collect(Collectors.toList());

    List<Hash32> idx0Proof = twoLevelTree.getProof(0, 2);
    List<Hash32> idx1Proof = twoLevelTree.getProof(1, 2);

    assertEquals(
        Arrays.asList(
            hashes.get(1), // H[1]
            hash.apply(Hash32.ZERO.concat(Hash32.ZERO)), // H(H[2] + H[3])
            twoLevelTree.encodeLength(sourceList.size()) // encoded length
            ),
        idx0Proof);

    assertEquals(
        Arrays.asList(
            hashes.get(0), // H[0]
            hash.apply(Hash32.ZERO.concat(Hash32.ZERO)), // H(H[2] + H[3])
            twoLevelTree.encodeLength(sourceList.size()) // encoded length
            ),
        idx1Proof);

    BeaconChainSpec spec = BeaconChainSpec.createWithDefaults();

    ReadList<Integer, DepositData> fourElementList = ReadList.wrap(sourceList, Integer::new, 4);
    Hash32 root = spec.hash_tree_root(fourElementList);
    assertTrue(
        spec.is_valid_merkle_branch(
            hashes.get(0), idx0Proof, UInt64.valueOf(treeDepth + 1), UInt64.valueOf(0), root));
    assertTrue(
        spec.is_valid_merkle_branch(
            hashes.get(1), idx1Proof, UInt64.valueOf(treeDepth + 1), UInt64.valueOf(1), root));
  }

  @Test
  public void proofIsValidForVariousNumberOfDeposits() {
    int maxCount = 16;

    BeaconChainSpec spec = BeaconChainSpec.createWithDefaults();
    MerkleTree<DepositData> tree =
        new DepositBufferedMerkle(
            spec.getHashFunction(),
            spec.getConstants().getDepositContractTreeDepth().getIntValue(),
            maxCount);

    List<DepositData> sourceList = new ArrayList<>();
    for (int count = 1; count < maxCount; count++) {
      sourceList.add(createDepositData(count - 1));
      tree.addValue(sourceList.get(count - 1));

      ReadList<Integer, DepositData> depositDataList =
          ReadList.wrap(
              sourceList,
              Integer::new,
              1L << spec.getConstants().getDepositContractTreeDepth().getIntValue());
      for (int i = 0; i < count; i++) {
        Hash32 leaf = spec.hash_tree_root(depositDataList.get(i));
        List<Hash32> proof = tree.getProof(i, count);
        assertTrue(
            spec.is_valid_merkle_branch(
                leaf,
                proof,
                spec.getConstants().getDepositContractTreeDepthPlusOne(),
                UInt64.valueOf(i),
                spec.hash_tree_root(depositDataList)));
      }
    }
  }

  private DepositData createDepositData(int num) {
    return new DepositData(
        BLSPubkey.wrap(
            Bytes48.leftPad(
                BytesValue.wrap(UInt64.valueOf(num).toBytes8LittleEndian().extractArray()))),
        Hash32.ZERO,
        Gwei.ZERO,
        BLSSignature.ZERO);
  }
}
