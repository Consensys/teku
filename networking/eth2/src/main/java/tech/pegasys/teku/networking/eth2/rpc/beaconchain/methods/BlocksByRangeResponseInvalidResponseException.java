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

import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public class BlocksByRangeResponseInvalidResponseException extends InvalidResponseException {

  public BlocksByRangeResponseInvalidResponseException(
      Peer peer, InvalidResponseType invalidResponseType) {
    super(
        String.format(
            "Received invalid response from peer %s: " + invalidResponseType.describe(), peer));
  }

  public BlocksByRangeResponseInvalidResponseException(InvalidResponseType invalidResponseType) {
    super("Received invalid response: " + invalidResponseType.describe());
  }

  public enum InvalidResponseType {
    BLOCK_SLOT_NOT_IN_RANGE("Block slot not in range"),
    BLOCK_SLOT_DOES_NOT_MATCH_STEP("Block slot does not match step"),
    BLOCK_SLOT_NOT_GREATER_THAN_PREVIOUS_BLOCK_SLOT(
        "Block slot not greater than previous block slot"),
    BLOCK_PARENT_ROOT_DOES_NOT_MATCH("Block parent root does not match previous block root");

    private final String description;

    InvalidResponseType(String description) {
      this.description = description;
    }

    public String describe() {
      return description;
    }
  }
}
