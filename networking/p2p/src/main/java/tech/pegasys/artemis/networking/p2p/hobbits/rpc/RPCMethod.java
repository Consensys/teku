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

package tech.pegasys.artemis.networking.p2p.hobbits.rpc;

/** Enumeration of supported RPC operations. */
public enum RPCMethod {
  HELLO(0),
  GOODBYE(1),
  GET_STATUS(2),
  GET_BLOCK_HEADERS(10),
  BLOCK_HEADERS(11),
  GET_BLOCK_BODIES(12),
  BLOCK_BODIES(13),
  GET_ATTESTATION(14),
  ATTESTATION(15);

  private int code;

  RPCMethod(int code) {
    this.code = code;
  }

  /** @return the encoded code of the RPC method */
  public int code() {
    return code;
  }

  /**
   * Finds the matching RPC method from its code.
   *
   * @param code the code
   * @return the matching RPC method
   * @throws IllegalArgumentException if no matching code exists.
   */
  static RPCMethod valueOf(int code) {
    switch (code) {
      case 0:
        return HELLO;
      case 1:
        return GOODBYE;
      case 2:
        return GET_STATUS;
      case 10:
        return GET_BLOCK_HEADERS;
      case 11:
        return BLOCK_HEADERS;
      case 12:
        return GET_BLOCK_BODIES;
      case 13:
        return BLOCK_BODIES;
      case 14:
        return GET_ATTESTATION;
      case 15:
        return ATTESTATION;

      default:
        throw new IllegalArgumentException("Unsupported code " + code);
    }
  }
}
