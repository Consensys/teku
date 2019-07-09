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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.hobbits.Message;
import org.apache.tuweni.plumtree.MessageSender;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.p2p.hobbits.gossip.GossipCodec;
import tech.pegasys.artemis.networking.p2p.hobbits.gossip.GossipMessage;

final class GossipCodecTest {

  @Test
  void testGossip() {
    BeaconBlock block = DataStructureUtil.randomBeaconBlock(Constants.GENESIS_SLOT);
    Message encoded =
        GossipCodec.encode(
            MessageSender.Verb.GOSSIP.ordinal(),
            "BLOCK",
            new Date().getTime(),
            Bytes32.random(),
            Bytes32.random(),
            block.toBytes());
    GossipMessage message = GossipCodec.decode(encoded);
    assertEquals(MessageSender.Verb.GOSSIP.ordinal(), message.method());
    BeaconBlock read = BeaconBlock.fromBytes(message.body());
    assertEquals(read.getSignature(), block.getSignature());
  }
}
