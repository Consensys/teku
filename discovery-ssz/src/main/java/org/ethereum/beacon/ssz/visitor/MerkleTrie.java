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

import java.util.Arrays;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class MerkleTrie {
  final BytesValue[] nodes;

  public MerkleTrie(BytesValue[] nodes) {
    this.nodes = nodes;
  }

  public Hash32 getPureRoot() {
    return Hash32.wrap(Bytes32.leftPad(nodes[1]));
  }

  public Hash32 getFinalRoot() {
    return Hash32.wrap(Bytes32.leftPad(nodes[0]));
  }

  public void setFinalRoot(Hash32 mixedInLengthHash) {
    nodes[0] = mixedInLengthHash;
  }

  public MerkleTrie copy() {
    return new MerkleTrie(Arrays.copyOf(nodes, nodes.length));
  }
}
