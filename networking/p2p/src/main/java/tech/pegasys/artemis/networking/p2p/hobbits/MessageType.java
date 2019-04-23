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

package tech.pegasys.artemis.networking.p2p.hobbits;

/** Enumeration of supported Gossip operations. */
public enum MessageType {
  BLOCK(0),
  ATTESTATION(1);

  private int code;

  MessageType(int code) {
    this.code = code;
  }

  /** @return the encoded code of the Gossip method */
  int code() {
    return code;
  }

  /**
   * Finds the matching Gossip method from its code.
   *
   * @param code the code
   * @return the matching Gossip method
   * @throws IllegalArgumentException if no matching code exists.
   */
  static MessageType valueOf(int code) {
    switch (code) {
      case 0:
        return BLOCK;
      case 1:
        return ATTESTATION;
      default:
        throw new IllegalArgumentException("Unsupported code " + code);
    }
  }
}
