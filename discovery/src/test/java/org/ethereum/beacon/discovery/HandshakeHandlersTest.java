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

package org.ethereum.beacon.discovery;

import static org.ethereum.beacon.discovery.TestUtil.NODE_RECORD_FACTORY_NO_VERIFICATION;
import static org.ethereum.beacon.discovery.TestUtil.TEST_SERIALIZER;
import static org.ethereum.beacon.discovery.pipeline.Field.BAD_PACKET;
import static org.ethereum.beacon.discovery.pipeline.Field.MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.PACKET_AUTH_HEADER_MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.PACKET_MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.SESSION;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.PipelineImpl;
import org.ethereum.beacon.discovery.pipeline.handler.AuthHeaderMessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessageHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.WhoAreYouPacketHandler;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.task.TaskMessageFactory;
import org.ethereum.beacon.discovery.task.TaskType;
import org.ethereum.beacon.schedulers.Scheduler;
import org.ethereum.beacon.schedulers.Schedulers;
import org.javatuples.Pair;
import org.junit.jupiter.api.Test;

// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.uint.UInt64;

public class HandshakeHandlersTest {

  @Test
  public void authHandlerWithMessageRoundTripTest() throws Exception {
    // Node1
    Pair<BytesValue, NodeRecord> nodePair1 = TestUtil.generateNode(30303);
    NodeRecord nodeRecord1 = nodePair1.getValue1();
    // Node2
    Pair<BytesValue, NodeRecord> nodePair2 = TestUtil.generateNode(30304);
    NodeRecord nodeRecord2 = nodePair2.getValue1();
    Random rnd = new Random();
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database1 = Database.inMemoryDB();
    Database database2 = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage1 =
        nodeTableStorageFactory.createTable(
            database1,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord1,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    add(nodeRecord2);
                  }
                });
    NodeBucketStorage nodeBucketStorage1 =
        nodeTableStorageFactory.createBucketStorage(database1, TEST_SERIALIZER, nodeRecord1);
    NodeTableStorage nodeTableStorage2 =
        nodeTableStorageFactory.createTable(
            database2,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord2,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    add(nodeRecord1);
                  }
                });
    NodeBucketStorage nodeBucketStorage2 =
        nodeTableStorageFactory.createBucketStorage(database2, TEST_SERIALIZER, nodeRecord2);

    // Node1 create AuthHeaderPacket
    final Packet[] outgoing1Packets = new Packet[2];
    final Semaphore outgoing1PacketsSemaphore = new Semaphore(2);
    outgoing1PacketsSemaphore.acquire(2);
    final Consumer<Packet> outgoingMessages1to2 =
        packet -> {
          System.out.println("Outgoing packet from 1 to 2: " + packet);
          outgoing1Packets[outgoing1PacketsSemaphore.availablePermits()] = packet;
          outgoing1PacketsSemaphore.release(1);
        };
    AuthTagRepository authTagRepository1 = new AuthTagRepository();
    NodeSession nodeSessionAt1For2 =
        new NodeSession(
            nodeRecord2,
            nodeRecord1,
            Bytes.wrap(nodePair1.getValue0().extractArray()),
            nodeTableStorage1.get(),
            nodeBucketStorage1,
            authTagRepository1,
            outgoingMessages1to2,
            rnd);
    final Consumer<Packet> outgoingMessages2to1 =
        packet -> {
          // do nothing, we don't need to test it here
        };
    NodeSession nodeSessionAt2For1 =
        new NodeSession(
            nodeRecord1,
            nodeRecord2,
            Bytes.wrap(nodePair2.getValue0().extractArray()),
            nodeTableStorage2.get(),
            nodeBucketStorage2,
            new AuthTagRepository(),
            outgoingMessages2to1,
            rnd);

    Scheduler taskScheduler = Schedulers.createDefault().events();
    Pipeline outgoingPipeline = new PipelineImpl().build();
    WhoAreYouPacketHandler whoAreYouPacketHandlerNode1 =
        new WhoAreYouPacketHandler(outgoingPipeline, taskScheduler);
    Envelope envelopeAt1From2 = new Envelope();
    byte[] idNonceBytes = new byte[32];
    Functions.getRandom().nextBytes(idNonceBytes);
    BytesValue idNonce = BytesValue.wrap(idNonceBytes);
    nodeSessionAt2For1.setIdNonce(Bytes.wrap(idNonce.extractArray()));
    BytesValue authTag = BytesValue.wrap(nodeSessionAt2For1.generateNonce().toArray());
    authTagRepository1.put(Bytes.wrap(authTag.extractArray()), nodeSessionAt1For2);
    envelopeAt1From2.put(
        Field.PACKET_WHOAREYOU,
        WhoAreYouPacket.create(
            nodePair1.getValue1().getNodeId(),
            Bytes.wrap(authTag.extractArray()),
            Bytes.wrap(idNonce.extractArray()),
            UInt64.ZERO));
    envelopeAt1From2.put(Field.SESSION, nodeSessionAt1For2);
    CompletableFuture<Void> future = new CompletableFuture<>();
    nodeSessionAt1For2.createNextRequest(TaskType.FINDNODE, future);
    whoAreYouPacketHandlerNode1.handle(envelopeAt1From2);
    assert outgoing1PacketsSemaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
    outgoing1PacketsSemaphore.release();

    // Node2 handle AuthHeaderPacket and finish handshake
    AuthHeaderMessagePacketHandler authHeaderMessagePacketHandlerNode2 =
        new AuthHeaderMessagePacketHandler(
            outgoingPipeline, taskScheduler, NODE_RECORD_FACTORY_NO_VERIFICATION);
    Envelope envelopeAt2From1 = new Envelope();
    envelopeAt2From1.put(PACKET_AUTH_HEADER_MESSAGE, outgoing1Packets[0]);
    envelopeAt2From1.put(SESSION, nodeSessionAt2For1);
    assertFalse(nodeSessionAt2For1.isAuthenticated());
    authHeaderMessagePacketHandlerNode2.handle(envelopeAt2From1);
    assertTrue(nodeSessionAt2For1.isAuthenticated());

    // Node 1 handles message from Node 2
    MessagePacketHandler messagePacketHandler1 = new MessagePacketHandler();
    Envelope envelopeAt1From2WithMessage = new Envelope();
    BytesValue pingAuthTag = BytesValue.wrap(nodeSessionAt1For2.generateNonce().toArray());
    MessagePacket pingPacketFrom2To1 =
        TaskMessageFactory.createPingPacket(
            Bytes.wrap(pingAuthTag.extractArray()),
            nodeSessionAt2For1,
            nodeSessionAt2For1
                .createNextRequest(TaskType.PING, new CompletableFuture<>())
                .getRequestId());
    envelopeAt1From2WithMessage.put(PACKET_MESSAGE, pingPacketFrom2To1);
    envelopeAt1From2WithMessage.put(SESSION, nodeSessionAt1For2);
    messagePacketHandler1.handle(envelopeAt1From2WithMessage);
    assertNull(envelopeAt1From2WithMessage.get(BAD_PACKET));
    assertNotNull(envelopeAt1From2WithMessage.get(MESSAGE));

    MessageHandler messageHandler = new MessageHandler(NODE_RECORD_FACTORY_NO_VERIFICATION);
    messageHandler.handle(envelopeAt1From2WithMessage);
    assert outgoing1PacketsSemaphore.tryAcquire(2, 1, TimeUnit.SECONDS);

    // Node 2 handles message from Node 1
    MessagePacketHandler messagePacketHandler2 = new MessagePacketHandler();
    Envelope envelopeAt2From1WithMessage = new Envelope();
    Packet pongPacketFrom1To2 = outgoing1Packets[1];
    MessagePacket pongMessagePacketFrom1To2 = (MessagePacket) pongPacketFrom1To2;
    envelopeAt2From1WithMessage.put(PACKET_MESSAGE, pongMessagePacketFrom1To2);
    envelopeAt2From1WithMessage.put(SESSION, nodeSessionAt1For2);
    messagePacketHandler2.handle(envelopeAt2From1WithMessage);
    assertNull(envelopeAt2From1WithMessage.get(BAD_PACKET));
    assertNotNull(envelopeAt2From1WithMessage.get(MESSAGE));
  }
}
