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
import static org.ethereum.beacon.discovery.TestUtil.SEED;
import static org.ethereum.beacon.discovery.TestUtil.TEST_SERIALIZER;
import static org.ethereum.beacon.discovery.util.Functions.PUBKEY_SIZE;
import static org.ethereum.beacon.discovery.util.Utils.extractBytesFromUnsignedBigInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.RandomPacket;
import org.ethereum.beacon.discovery.packet.UnknownPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.EnrFieldV4;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.util.Functions;
import org.javatuples.Pair;
import org.junit.jupiter.api.Assertions;
import org.web3j.crypto.ECKeyPair;
import reactor.core.publisher.Flux;

/** Same as {@link DiscoveryNoNetworkTest} but using real network */
public class DiscoveryNetworkInteropTest {

  private Function<UInt64, NodeRecord> homeNodeSupplier =
      (oldSeq) -> {
        try {
          return NODE_RECORD_FACTORY_NO_VERIFICATION.createFromValues(
              UInt64.valueOf(1),
              Bytes.EMPTY,
              new ArrayList<Pair<String, Object>>() {
                {
                  add(Pair.with(EnrField.ID, IdentitySchema.V4));
                  add(
                      Pair.with(
                          EnrFieldV4.IP_V4,
                          Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress())));
                  add(Pair.with(EnrFieldV4.UDP_V4, 30303));
                  add(
                      Pair.with(
                          EnrFieldV4.PKEY_SECP256K1,
                          Bytes.fromHexString(
                              "0bfb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
                }
              });
        } catch (UnknownHostException e) {
          throw new RuntimeException(e);
        }
      };

  //  @Test
  public void testLighthouseInterop() throws Exception {
    //    final String remoteHostEnr =
    // "-IS4QJBOCmTBOuIE0_z16nV8P1KOyVVIu1gq2S83H5HBmfFaFuevJT0XyKH35LNVxHK5dotDTwqlc9NiRXosBcQ1bJ8BgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIyk";
    final String remoteHostEnr =
        "-IS4QBKM9XJGAZDA3eqFuII55lEceslZhHcm8OIyfYQzw_MgVSyFEB4hVcs7tT1DhoF_1xXCo-eyRf4_1I2VlaGtaIUBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQJ2-TT2v3owdiYd2cGcxy0dk_y3vn5DX8KXuijG50EXIoN1ZHCCIy0";
    //
    // -IS4QOrJvO6_CDyN0dwE9R8NzUR9CK4v0t_Q6l8EKhMhGhCpKXLMQNYUXbMYN-j6kPjAczrQ1uAwWXAI8PjMGXsJxRMBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQJI4MROfzgMfjN1ANb-9fNXFT3xnjzK5NEfNLG4oiMPDoN1ZHCCIy0

    // NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(remoteHostEnr);
    NodeRecord remoteNodeRecord = NodeRecordFactory.DEFAULT.fromBase64(remoteHostEnr);
    //    NodeRecord remoteNodeRecord =
    // NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(remoteHostEnr);
    remoteNodeRecord.verify();
    Assertions.assertNotNull(remoteNodeRecord);
    System.out.println("remoteEnr:" + remoteNodeRecord.asBase64());
    System.out.println("remoteNodeId:" + remoteNodeRecord.getNodeId());
    System.out.println("remoteNodeRecord:" + remoteNodeRecord);

    Pair<NodeRecord, byte[]> localNodeInfo = createLocalNodeRecord(9002);
    NodeRecord localNodeRecord = localNodeInfo.getValue0();
    System.out.println("localNodeEnr:" + localNodeRecord.asBase64());
    System.out.println("localNodeId:" + localNodeRecord.getNodeId());
    System.out.println("localNodeRecord:" + localNodeRecord);

    byte[] localPrivKey = localNodeInfo.getValue1();

    Database database0 = Database.inMemoryDB();
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    NodeTableStorage nodeTableStorage0 =
        nodeTableStorageFactory.createTable(
            database0,
            TEST_SERIALIZER,
            (oldSeq) -> localNodeRecord,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    //                    add(remoteNodeRecord);
                  }
                });

    NodeBucketStorage nodeBucketStorage0 =
        nodeTableStorageFactory.createBucketStorage(database0, TEST_SERIALIZER, localNodeRecord);

    DiscoveryManagerImpl discoveryManager0 =
        new DiscoveryManagerImpl(
            nodeTableStorage0.get(),
            nodeBucketStorage0,
            localNodeRecord,
            Bytes.wrap(localPrivKey),
            NODE_RECORD_FACTORY_NO_VERIFICATION,
            Schedulers.createDefault().newSingleThreadDaemon("server-1"),
            Schedulers.createDefault().newSingleThreadDaemon("client-1"));

    CountDownLatch randomSent1to2 = new CountDownLatch(1);
    CountDownLatch whoareyouSent2to1 = new CountDownLatch(1);
    CountDownLatch authPacketSent1to2 = new CountDownLatch(1);
    CountDownLatch nodesSent2to1 = new CountDownLatch(1);

    //    Flux.from(discoveryManager0.getOutgoingMessages())
    //        .map(p -> new UnknownPacket(p.getPacket().getBytes()))
    //        .subscribe(
    //            networkPacket -> {
    //              // 1 -> 2 random
    //              if (randomSent1to2.getCount() != 0) {
    //                RandomPacket randomPacket = networkPacket.getRandomPacket();
    //                System.out.println("1 => 2: " + randomPacket);
    //                randomSent1to2.countDown();
    //              } else if (authPacketSent1to2.getCount() != 0) {
    //                // 1 -> 2 auth packet with FINDNODES
    //                AuthHeaderMessagePacket authHeaderMessagePacket =
    //                    networkPacket.getAuthHeaderMessagePacket();
    //                System.out.println("1 => 2: " + authHeaderMessagePacket);
    //                authPacketSent1to2.countDown();
    //              } else {
    //                throw new RuntimeException("Not expected!");
    //              }
    //            });

    Flux.from(discoveryManager0.getOutgoingMessages())
        .map(p -> new UnknownPacket(p.getPacket().getBytes()))
        .subscribe(
            networkPacket -> {
              // 1 -> 2 random
              if (randomSent1to2.getCount() != 0) {
                RandomPacket randomPacket = networkPacket.getRandomPacket();
                System.out.println("1 => 2: " + randomPacket);
                randomSent1to2.countDown();
              } else if (authPacketSent1to2.getCount() != 0) {
                // 1 -> 2 auth packet with FINDNODES
                AuthHeaderMessagePacket authHeaderMessagePacket =
                    networkPacket.getAuthHeaderMessagePacket();
                System.out.println("1 => 2: " + authHeaderMessagePacket);
                authPacketSent1to2.countDown();
              }

              // 2 -> 1 whoareyou
              else if (whoareyouSent2to1.getCount() != 0) {
                WhoAreYouPacket whoAreYouPacket = networkPacket.getWhoAreYouPacket();
                System.out.println("2 => 1: " + whoAreYouPacket);
                whoareyouSent2to1.countDown();
              } else {
                // 2 -> 1 nodes
                MessagePacket messagePacket = networkPacket.getMessagePacket();
                System.out.println("2 => 1: " + messagePacket);
                nodesSent2to1.countDown();
              }
            });

    discoveryManager0.start();

    discoveryManager0.findNodes(remoteNodeRecord, 0);

    while (true) {
      Thread.sleep(10000);
      discoveryManager0.ping(remoteNodeRecord);
    }
  }

  Random rnd = new Random(SEED);

  public Pair<NodeRecord, byte[]> createLocalNodeRecord(int port) {

    try {
      // set local service node
      byte[] privKey1 = new byte[32];
      rnd.nextBytes(privKey1);
      ECKeyPair keyPair1 = ECKeyPair.create(privKey1);

      //      org.apache.milagro.amcl.SECP256K1.ECP ecp =
      //          ECP.fromBytes(keyPair1.getPublicKey().toByteArray());

      //      byte[] pubbytes = new byte[33];
      //      ecp.toBytes(pubbytes, true);

      Bytes localAddressBytes = Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress());
      Bytes localIp1 =
          Bytes.concatenate(Bytes.wrap(new byte[4 - localAddressBytes.size()]), localAddressBytes);
      NodeRecord nodeRecord1 =
          NodeRecordFactory.DEFAULT.createFromValues(
              //          NODE_RECORD_FACTORY_NO_VERIFICATION.createFromValues(
              UInt64.ZERO,
              Bytes.EMPTY,
              Pair.with(EnrField.ID, IdentitySchema.V4),
              Pair.with(EnrField.IP_V4, localIp1),
              Pair.with(
                  EnrFieldV4.PKEY_SECP256K1,
                  Functions.derivePublicKeyFromPrivate(Bytes.wrap(privKey1))),
              //
              // Bytes.wrap(extractBytesFromUnsignedBigInt(keyPair1.getPublicKey()))),
              Pair.with(EnrField.TCP_V4, port),
              Pair.with(EnrField.UDP_V4, port));
      Bytes signature1 = Functions.sign(Bytes.wrap(privKey1), nodeRecord1.serializeNoSignature());
      nodeRecord1.setSignature(signature1);
      nodeRecord1.verify();
      return new Pair(nodeRecord1, privKey1);
    } catch (Exception e) {
      e.printStackTrace();
      Assertions.fail();
    }
    return null;
  }

  //  @Test
  public void testLighthouseInterop1() throws Exception {
    // lighthout Base64 ENR:
    final String remoteHostEnr =
        "-IS4QJBOCmTBOuIE0_z16nV8P1KOyVVIu1gq2S83H5HBmfFaFuevJT0XyKH35LNVxHK5dotDTwqlc9NiRXosBcQ1bJ8BgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIyk";

    // create a NodeRecord with their ENR base64 record
    // we actually use the default node factory in order to do actual verification
    // the alternative is to use type NODE_RECORD_FACTORY_NO_VERIFICATION
    // NodeRecordFactory.DEFAULT is the verifying factory
    NodeRecord remoteHostNodeRecord = NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(remoteHostEnr);
    System.out.println("remoteHostNodeRecord:" + remoteHostNodeRecord);

    Assertions.assertNotNull(remoteHostNodeRecord);

    // the following is working through the note table based on distance
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage =
        nodeTableStorageFactory.createTable(
            database,
            TEST_SERIALIZER,
            homeNodeSupplier,
            () -> {
              List<NodeRecord> nodes = new ArrayList<>();
              nodes.add(remoteHostNodeRecord);
              return nodes;
            });

    // node is adjusted to be close to localhostEnr
    NodeRecord closestNode =
        NODE_RECORD_FACTORY_NO_VERIFICATION.createFromValues(
            UInt64.valueOf(1),
            Bytes.EMPTY,
            new ArrayList<Pair<String, Object>>() {
              {
                add(Pair.with(EnrField.ID, IdentitySchema.V4));
                add(
                    Pair.with(
                        EnrFieldV4.IP_V4,
                        Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress())));
                add(Pair.with(EnrFieldV4.UDP_V4, 9002));
                add(
                    Pair.with(
                        EnrFieldV4.PKEY_SECP256K1,
                        Bytes.fromHexString(
                            "aafb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
              }
            });
    nodeTableStorage.get().save(new NodeRecordInfo(closestNode, -1L, NodeStatus.ACTIVE, 0));
    assertEquals(
        nodeTableStorage
            .get()
            .getNode(closestNode.getNodeId())
            .get()
            .getNode()
            .get(EnrFieldV4.PKEY_SECP256K1)
            .toString()
            .toUpperCase(),
        closestNode.get(EnrFieldV4.PKEY_SECP256K1).toString().toUpperCase());
    NodeRecord farNode =
        NODE_RECORD_FACTORY_NO_VERIFICATION.createFromValues(
            UInt64.valueOf(1),
            Bytes.EMPTY,
            new ArrayList<Pair<String, Object>>() {
              {
                add(Pair.with(EnrField.ID, IdentitySchema.V4));
                add(
                    Pair.with(
                        EnrFieldV4.IP_V4,
                        Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress())));
                add(Pair.with(EnrFieldV4.UDP_V4, 9003));
                add(
                    Pair.with(
                        EnrFieldV4.PKEY_SECP256K1,
                        Bytes.fromHexString(
                            "bafb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
              }
            });
    nodeTableStorage.get().save(new NodeRecordInfo(farNode, -1L, NodeStatus.ACTIVE, 0));
    List<NodeRecordInfo> closestNodes =
        nodeTableStorage.get().findClosestNodes(closestNode.getNodeId(), 252);
    assertEquals(2, closestNodes.size());
    Set<Bytes> publicKeys = new HashSet<>();
    closestNodes.forEach(
        n -> {
          Object key3 = n.getNode().get(EnrFieldV4.PKEY_SECP256K1);
          publicKeys.add((Bytes) key3);
        });
    assertTrue(publicKeys.contains(remoteHostNodeRecord.get(EnrFieldV4.PKEY_SECP256K1)));
    assertTrue(publicKeys.contains(closestNode.get(EnrFieldV4.PKEY_SECP256K1)));
    List<NodeRecordInfo> farNodes = nodeTableStorage.get().findClosestNodes(farNode.getNodeId(), 1);
    assertEquals(1, farNodes.size());
    assertEquals(
        farNodes.get(0).getNode().get(EnrFieldV4.PKEY_SECP256K1).toString().toUpperCase(),
        farNode.get(EnrFieldV4.PKEY_SECP256K1).toString().toUpperCase());

    Random rnd = new Random(SEED);
    // set local service node
    byte[] privKey1 = new byte[32];
    rnd.nextBytes(privKey1);
    ECKeyPair keyPair1 = ECKeyPair.create(privKey1);
    Bytes localIp1 = Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress());
    NodeRecord nodeRecord1 =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ZERO,
            Bytes.EMPTY,
            Pair.with(EnrField.ID, IdentitySchema.V4),
            Pair.with(EnrField.IP_V4, localIp1),
            Pair.with(EnrField.TCP_V4, 9004),
            Pair.with(EnrField.UDP_V4, 9004),
            Pair.with(
                EnrFieldV4.PKEY_SECP256K1,
                Bytes.wrap(extractBytesFromUnsignedBigInt(keyPair1.getPublicKey(), PUBKEY_SIZE))));
    Bytes signature1 = Functions.sign(Bytes.wrap(privKey1), nodeRecord1.serializeNoSignature());
    nodeRecord1.setSignature(signature1);
    nodeRecord1.verify();

    /// use discovery manager to connect to a remote discv5 peer
    Database database0 = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage0 =
        nodeTableStorageFactory.createTable(
            database0,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord1,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    add(remoteHostNodeRecord);
                  }
                });

    NodeBucketStorage nodeBucketStorage1 =
        nodeTableStorageFactory.createBucketStorage(database0, TEST_SERIALIZER, nodeRecord1);
    DiscoveryManagerImpl discoveryManager1 =
        new DiscoveryManagerImpl(
            nodeTableStorage0.get(),
            nodeBucketStorage1,
            nodeRecord1,
            Bytes.wrap(privKey1),
            NODE_RECORD_FACTORY_NO_VERIFICATION,
            Schedulers.createDefault().newSingleThreadDaemon("server-1"),
            Schedulers.createDefault().newSingleThreadDaemon("client-1"));

    Flux.from(discoveryManager1.getOutgoingMessages())
        .map(p -> new UnknownPacket(p.getPacket().getBytes()))
        .subscribe(
            networkPacket -> {
              // 1 -> 2 random
              RandomPacket randomPacket = networkPacket.getRandomPacket();
              System.out.println("1 => 2: " + randomPacket);

              // 1 -> 2 auth packet with FINDNODES
              AuthHeaderMessagePacket authHeaderMessagePacket =
                  networkPacket.getAuthHeaderMessagePacket();
              System.out.println("1 => 2: " + authHeaderMessagePacket);
            });

    // 4) fire 1 to 2 dialog
    discoveryManager1.start();
    CompletableFuture<Void> voidCompletableFuture =
        discoveryManager1.findNodes(remoteHostNodeRecord, 0);

    voidCompletableFuture.get();
  }

  //  @Test
  public void testClient() throws Exception {

    Random rnd = new Random(SEED);
    byte[] privKey = new byte[32];
    rnd.nextBytes(privKey);
    ECKeyPair keyPair = ECKeyPair.create(privKey);
    Bytes localIp = Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress());
    NodeRecord nodeRecord0 =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ZERO,
            Bytes.EMPTY,
            Pair.with(EnrField.ID, IdentitySchema.V4),
            Pair.with(EnrField.IP_V4, localIp),
            Pair.with(EnrField.TCP_V4, 9001),
            Pair.with(EnrField.UDP_V4, 9001),
            Pair.with(
                EnrFieldV4.PKEY_SECP256K1,
                Bytes.wrap(extractBytesFromUnsignedBigInt(keyPair.getPublicKey(), PUBKEY_SIZE))));
    Bytes signature0 = Functions.sign(Bytes.wrap(privKey), nodeRecord0.serializeNoSignature());
    nodeRecord0.setSignature(signature0);
    //    nodeRecord0.verify();

  }

  public void testServer() throws Exception {
    //    DiscoveryServer ds = new DiscoveryServerImpl();
  }

  //  @Test
  public void testLocalInterconnect() throws Exception {
    Random rnd = new Random(SEED);

    // set local service node
    byte[] privKey1 = new byte[32];
    rnd.nextBytes(privKey1);
    ECKeyPair keyPair1 = ECKeyPair.create(privKey1);
    Bytes localIp1 = Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress());
    NodeRecord nodeRecord1 =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ZERO,
            Bytes.EMPTY,
            Pair.with(EnrField.ID, IdentitySchema.V4),
            Pair.with(EnrField.IP_V4, localIp1),
            Pair.with(EnrField.TCP_V4, 9002),
            Pair.with(EnrField.UDP_V4, 9002),
            Pair.with(
                EnrFieldV4.PKEY_SECP256K1,
                Bytes.wrap(extractBytesFromUnsignedBigInt(keyPair1.getPublicKey(), PUBKEY_SIZE))));
    Bytes signature1 = Functions.sign(Bytes.wrap(privKey1), nodeRecord1.serializeNoSignature());
    nodeRecord1.setSignature(signature1);
    nodeRecord1.verify();

    // set remote service
    byte[] privKey = new byte[32];
    rnd.nextBytes(privKey);
    ECKeyPair keyPair = ECKeyPair.create(privKey);
    Bytes localIp = Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress());
    NodeRecord nodeRecord0 =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.ZERO,
            Bytes.EMPTY,
            Pair.with(EnrField.ID, IdentitySchema.V4),
            Pair.with(EnrField.IP_V4, localIp),
            Pair.with(EnrField.TCP_V4, 9001),
            Pair.with(EnrField.UDP_V4, 9001),
            Pair.with(
                EnrFieldV4.PKEY_SECP256K1,
                Bytes.wrap(extractBytesFromUnsignedBigInt(keyPair.getPublicKey(), PUBKEY_SIZE))));
    Bytes signature0 = Functions.sign(Bytes.wrap(privKey), nodeRecord0.serializeNoSignature());
    nodeRecord0.setSignature(signature0);
    nodeRecord0.verify();

    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();

    Database database0 = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage0 =
        nodeTableStorageFactory.createTable(
            database0,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord1,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    add(nodeRecord0);
                  }
                });

    NodeBucketStorage nodeBucketStorage1 =
        nodeTableStorageFactory.createBucketStorage(database0, TEST_SERIALIZER, nodeRecord1);
    DiscoveryManagerImpl discoveryManager1 =
        new DiscoveryManagerImpl(
            nodeTableStorage0.get(),
            nodeBucketStorage1,
            nodeRecord1,
            Bytes.wrap(privKey1),
            NODE_RECORD_FACTORY_NO_VERIFICATION,
            Schedulers.createDefault().newSingleThreadDaemon("server-1"),
            Schedulers.createDefault().newSingleThreadDaemon("client-1"));

    // 3) Expect standard 1 => 2 dialog
    CountDownLatch randomSent1to2 = new CountDownLatch(1);
    CountDownLatch whoareyouSent2to1 = new CountDownLatch(1);
    CountDownLatch authPacketSent1to2 = new CountDownLatch(1);
    CountDownLatch nodesSent2to1 = new CountDownLatch(1);

    Flux.from(discoveryManager1.getOutgoingMessages())
        .map(p -> new UnknownPacket(p.getPacket().getBytes()))
        .subscribe(
            networkPacket -> {
              // 1 -> 2 random
              if (randomSent1to2.getCount() != 0) {
                RandomPacket randomPacket = networkPacket.getRandomPacket();
                System.out.println("1 => 2: " + randomPacket);
                randomSent1to2.countDown();
              } else if (authPacketSent1to2.getCount() != 0) {
                // 1 -> 2 auth packet with FINDNODES
                AuthHeaderMessagePacket authHeaderMessagePacket =
                    networkPacket.getAuthHeaderMessagePacket();
                System.out.println("1 => 2: " + authHeaderMessagePacket);
                authPacketSent1to2.countDown();
              } else {
                throw new RuntimeException("Not expected!");
              }
            });

    // 4) fire 1 to 2 dialog
    discoveryManager1.start();
    discoveryManager1.findNodes(nodeRecord0, 0);

    //    assert randomSent1to2.await(10, TimeUnit.SECONDS);
    //    assert whoareyouSent2to1.await(10, TimeUnit.SECONDS);
    //    int distance1To2 = Functions.logDistance(nodeRecord1.getNodeId(),
    // nodeRecord2.getNodeId());
    //    assertFalse(nodeBucketStorage1.get(distance1To2).isPresent());
    //    assert authPacketSent1to2.await(10, TimeUnit.SECONDS);
    //    assert nodesSent2to1.await(10, TimeUnit.SECONDS);
    //    Thread.sleep(500);
    // 1 sent findnodes to 2, received only (2) in answer, because 3 is not checked
    // 1 added 2 to its nodeBuckets, because its now checked, but not before
    //    NodeBucket bucketAt1With2 = nodeBucketStorage1.get(distance1To2).get();
  }
}
