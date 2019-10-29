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

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Not a unit test. Just some experiments with Progressive Merkle Trie
 * https://github.com/ethereum/research/blob/master/beacon_chain_impl/progressive_merkle_tree.py
 */
public class NewMerkleTest {
  int depth = 3;
  String[] zerohashes = new String[depth];

  public NewMerkleTest() {
    zerohashes[0] = "0";
    for (int i = 1; i < depth; i++) {
      zerohashes[i] = hash(zerohashes[i - 1] + zerohashes[i - 1]);
    }
  }

  String hash(String s1) {
    return "(" + s1 + ")";
  }

  String simpleMerkleRoot(String... values_) {
    ArrayList<String> values = new ArrayList<>(Arrays.asList(values_));
    for (int h = 0; h < depth; h++) {
      if (values.size() % 2 == 1) values.add(zerohashes[h]);
      ArrayList<String> values1 = new ArrayList<>();
      for (int i = 0; i < values.size(); i += 2) {
        values1.add(hash(values.get(i) + values.get(i + 1)));
      }
      values = values1;
    }
    return values.get(0);
  }

  void addValue(String[] branch, int index, String value) {
    /*
       i = 0
       while (index+1) % 2**(i+1) == 0:
           i += 1
       for j in range(0, i):
           value = hash(branch[j] + value)
           # branch[j] = zerohashes[j]
       branch[i] = value
    */
    int i = 0;
    while ((index + 1) % (1 << (i + 1)) == 0) {
      i += 1;
    }
    for (int j = 0; j < i; j++) {
      value = hash(branch[j] + value);
    }
    branch[i] = value;
  }

  String getRootFromBranch(String[] branch, int size) {
    String r = "0";
    for (int h = 0; h < depth; h++) {
      if ((size >> h) % 2 == 1) {
        r = hash(branch[h] + r);
      } else {
        r = hash(r + zerohashes[h]);
      }
    }
    return r;
  }
  /*
  def get_root_from_branch(branch, size):
    r = b'\x00' * 32
    for h in range(32):
        if (size >> h) % 2 == 1:
            r = hash(branch[h] + r)
        else:
            r = hash(r + zerohashes[h])
    return r

   */

  public static void main(String[] args) throws Exception {
    NewMerkleTest t = new NewMerkleTest();

    String[] branch = Arrays.copyOf(t.zerohashes, t.depth);
    System.out.println(Arrays.toString(branch));
    t.addValue(branch, 0, "a");
    System.out.println(Arrays.toString(branch));
    System.out.println(t.simpleMerkleRoot("a"));
    System.out.println(t.getRootFromBranch(branch, 1));
    t.addValue(branch, 1, "b");
    System.out.println(Arrays.toString(branch));
    System.out.println(t.simpleMerkleRoot("a", "b"));
    System.out.println(t.getRootFromBranch(branch, 2));
    t.addValue(branch, 2, "c");
    System.out.println(Arrays.toString(branch));
    System.out.println(t.simpleMerkleRoot("a", "b", "c"));
    System.out.println(t.getRootFromBranch(branch, 3));
    t.addValue(branch, 3, "d");
    System.out.println(Arrays.toString(branch));
    System.out.println(t.simpleMerkleRoot("a", "b", "c", "d"));
    System.out.println(t.getRootFromBranch(branch, 4));
    t.addValue(branch, 4, "e");
    System.out.println(Arrays.toString(branch));
    System.out.println(t.simpleMerkleRoot("a", "b", "c", "d", "e"));
    System.out.println(t.getRootFromBranch(branch, 5));
  }
}
