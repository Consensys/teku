/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.hashtree;

import org.apache.tuweni.bytes.Bytes32;

public class HashLink {
  private final Bytes32 childHash;
  private final Bytes32 parentHash;

  public HashLink(Bytes32 childHash, Bytes32 parentHash) {
    this.childHash = childHash;
    this.parentHash = parentHash;
  }

  public Bytes32 getChildHash() {
    return childHash;
  }

  public Bytes32 getParentHash() {
    return parentHash;
  }
}
