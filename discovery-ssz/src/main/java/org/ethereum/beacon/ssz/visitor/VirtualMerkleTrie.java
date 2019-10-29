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

import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** Replaces root of {@link MerkleTrie}, replicating interface */
public class VirtualMerkleTrie extends MerkleTrie {
  private Hash32 root;

  public VirtualMerkleTrie(BytesValue[] nodes, BytesValue root) {
    super(nodes);
    this.root = Hash32.wrap(Bytes32.leftPad(root));
  }

  @Override
  public Hash32 getPureRoot() {
    return root;
  }

  @Override
  public Hash32 getFinalRoot() {
    return root;
  }

  @Override
  public void setFinalRoot(Hash32 finalRoot) {
    this.root = finalRoot;
  }

  @Override
  public VirtualMerkleTrie copy() {
    return new VirtualMerkleTrie(super.nodes, root.copy());
  }
}
