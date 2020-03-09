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

package tech.pegasys.artemis.networking.p2p.discovery.tuweniv5;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.concurrent.AsyncCompletion;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.crypto.SECP256K1.KeyPair;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.apache.tuweni.devp2p.EthereumNodeRecord;
import org.apache.tuweni.devp2p.v5.DefaultNodeDiscoveryService;
import org.apache.tuweni.devp2p.v5.ENRStorage;
import org.apache.tuweni.devp2p.v5.NodeDiscoveryService;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.artemis.networking.p2p.discovery.DiscoveryService;
import tech.pegasys.artemis.util.async.SafeFuture;

public class TuweniDiscoveryService implements DiscoveryService {

  private final NodeDiscoveryService discoveryService;
  private final CustomEnrStorage enrStorage;
  private final String enr;

  public TuweniDiscoveryService(
      final NodeDiscoveryService discoveryService,
      final CustomEnrStorage enrStorage,
      final String enr) {
    this.discoveryService = discoveryService;
    this.enrStorage = enrStorage;
    this.enr = enr;
  }

  public static DiscoveryService create(
      final Bytes privateKey, final String address, final int port, final List<String> bootnodes) {
    final CustomEnrStorage enrStorage = new CustomEnrStorage();
    Bytes32 privKeyBytes;
    if (privateKey.size() == 33) {
      privKeyBytes = Bytes32.wrap(privateKey.slice(1, 32));
    } else {
      privKeyBytes = Bytes32.wrap(privateKey);
    }
    final KeyPair keyPair = KeyPair.fromSecretKey(SecretKey.fromBytes(Bytes32.wrap(privKeyBytes)));

    final long enrSeq = Instant.now().toEpochMilli();
    final InetSocketAddress bindAddress = new InetSocketAddress(address, port);
    final NodeDiscoveryService discoveryService =
        DefaultNodeDiscoveryService.open(keyPair, port, bindAddress, enrSeq, bootnodes, enrStorage);
    final String enr =
        "enr:"
            + Base64.getUrlEncoder()
                .encodeToString(
                    EthereumNodeRecord.toRLP(
                            keyPair,
                            enrSeq,
                            Collections.emptyMap(),
                            bindAddress.getAddress(),
                            port,
                            port)
                        .toArrayUnsafe());
    return new TuweniDiscoveryService(discoveryService, enrStorage, enr);
  }

  @Override
  public SafeFuture<?> start() {
    final SafeFuture<?> result = new SafeFuture<>();
    final AsyncCompletion asyncCompletion = discoveryService.startAsync();
    asyncCompletion.whenComplete(
        error -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(null);
          }
        });
    return result;
  }

  @Override
  public SafeFuture<?> stop() {
    discoveryService.terminateAsync();
    return SafeFuture.COMPLETE;
  }

  @Override
  public Stream<DiscoveryPeer> streamKnownPeers() {
    return enrStorage
        .streamRecords()
        .map(
            record -> {
              final Bytes publicKey = Bytes.wrap(record.publicKey().asEcPoint().getEncoded(true));
              return new DiscoveryPeer(publicKey, new InetSocketAddress(record.ip(), record.tcp()));
            });
  }

  @Override
  public CompletableFuture<Void> searchForPeers() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public Optional<String> getEnr() {
    return Optional.of(enr);
  }

  private static class CustomEnrStorage implements ENRStorage {
    private ConcurrentHashMap<Bytes, Bytes> storage = new ConcurrentHashMap<>();

    @Override
    public Bytes find(final Bytes nodeid) {
      return storage.get(nodeid);
    }

    @Override
    public void put(final Bytes nodeId, final Bytes enr) {
      System.out.println("Storing node: " + nodeId);
      storage.put(nodeId, enr);
    }

    @Override
    public void set(final Bytes enr) {
      put(Hash.sha2_256(enr), enr);
    }

    public Stream<EthereumNodeRecord> streamRecords() {
      return storage.values().stream().map(EthereumNodeRecord::fromRLP);
    }
  }
}
