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
import org.ethereum.beacon.discovery.BytesValue;
import org.ethereum.beacon.discovery.Hashes;

// import org.ethereum.beacon.crypto.Hashes;
// import tech.pegasys.artemis.util.bytes.Bytes;
// import tech.pegasys.artemis.util.bytes.Bytess;
// import tech.pegasys.artemis.util.bytes.Bytes;

/** Default packet form until its goal is known */
public class UnknownPacket extends AbstractPacket {

  public UnknownPacket(BytesValue bytes) {
    super(bytes);
  }

  public MessagePacket getMessagePacket() {
    return new MessagePacket(getBytes());
  }

  public AuthHeaderMessagePacket getAuthHeaderMessagePacket() {
    return new AuthHeaderMessagePacket(getBytes());
  }

  public RandomPacket getRandomPacket() {
    return new RandomPacket(getBytes());
  }

  public WhoAreYouPacket getWhoAreYouPacket() {
    return new WhoAreYouPacket(getBytes());
  }

  public boolean isWhoAreYouPacket(Bytes destNodeId) {
    return WhoAreYouPacket.getStartMagic(destNodeId).equals(getBytes().slice(0, 32));
  }

  // tag              = xor(sha256(dest-node-id), src-node-id)
  // dest-node-id     = 32-byte node ID of B
  // src-node-id      = 32-byte node ID of A
  //
  // The recipient can recover the sender's ID by performing the same calculation in reverse.
  //
  // src-node-id      = xor(sha256(dest-node-id), tag)
  public Bytes getSourceNodeId(Bytes destNodeId) {
    assert !isWhoAreYouPacket(destNodeId);
    Bytes xorTag = Bytes.wrap(getBytes().toArray()).slice(0, 32);
    return Bytes.wrap(Hashes.sha256(BytesValue.wrap(destNodeId.toArray())).extractArray())
        .xor(Bytes.wrap(Bytes.concatenate(xorTag, Bytes.of(0))));
  }

  @Override
  public String toString() {
    return "UnknownPacket{"
        + (getBytes().size() < 200
            ? getBytes()
            : getBytes().slice(0, 190) + "..." + "(" + getBytes().size() + " bytes)")
        + "}";
  }
}
