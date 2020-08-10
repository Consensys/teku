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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class BlocksByRangeResponseOutOfOrderException extends InvalidResponseException {

  public BlocksByRangeResponseOutOfOrderException(Peer peer, UInt64 startSlot, UInt64 endSlot) {
    super(
        String.format(
            "Received invalid response from peer %s.  Peer returned blocks at slots %s, %s out of order. ",
            peer, startSlot, endSlot));
  }
}
