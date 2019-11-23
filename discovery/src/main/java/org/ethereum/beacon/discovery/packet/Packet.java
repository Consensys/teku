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

package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Network packet as defined by discovery v5 specification. See <a
 * href="https://github.com/ethereum/devp2p/blob/master/discv5/discv5-wire.md#packet-encoding">https://github.com/ethereum/devp2p/blob/master/discv5/discv5-wire.md#packet-encoding</a>
 */
public interface Packet {

  static Bytes createTag(Bytes homeNodeId, Bytes destNodeId) {
    return homeNodeId.xor(Functions.hash(destNodeId), MutableBytes.create(destNodeId.size()));
  }

  Bytes getBytes();

  Bytes getBytesValue();
}
