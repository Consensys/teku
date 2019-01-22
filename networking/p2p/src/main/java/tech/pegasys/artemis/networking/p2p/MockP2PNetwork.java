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

package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.beaconchainblocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.beaconchainoperations.Attestation;
import tech.pegasys.artemis.datastructures.beaconchainoperations.AttestationData;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;

public class MockP2PNetwork implements P2PNetwork {

  private final EventBus eventBus;
  private static final Logger LOG = LogManager.getLogger();

  public MockP2PNetwork(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  /**
   * Returns a snapshot of the currently connected peer connections.
   *
   * @return Peers currently connected.
   */
  @Override
  public Collection<?> getPeers() {
    return null;
  }

  /**
   * Connects to a {@link Peer}.
   *
   * @param peer Peer to connect to.
   * @return Future of the established {@link PeerConnection}
   */
  @Override
  public CompletableFuture<?> connect(String peer) {
    return null;
  }

  /**
   * Subscribe a to all incoming events.
   *
   * @param event to subscribe to.
   */
  @Override
  public void subscribe(String event) {}

  /** Stops the P2P network layer. */
  @Override
  public void stop() {}

  /**
   * Checks if the node is listening for network connections
   *
   * @return true if the node is listening for network connections, false, otherwise.
   */
  @Override
  public boolean isListening() {
    return false;
  }

  @Override
  public void run() {
    this.simulateNewMessages();
    // this.simulateNewAttestations();
  }

  @Override
  public void close() {
    this.stop();
  }

  private void simulateNewMessages() {
    try {
      while (true) {
        Random random = new Random();
        long n = 1000L * Integer.toUnsignedLong(random.nextInt(7) + 2);
        Thread.sleep(n);
        if (n % 3 == 0) {
          BeaconBlock block = new BeaconBlock();
          this.eventBus.post(block);
        } else {

          AttestationData data =
              new AttestationData(
                  n,
                  UnsignedLong.ZERO,
                  Bytes32.ZERO,
                  Bytes32.ZERO,
                  Bytes32.ZERO,
                  Bytes32.ZERO,
                  UnsignedLong.ZERO,
                  Bytes32.ZERO);
          Attestation attestation = new Attestation(data, Bytes32.ZERO, Bytes32.ZERO, null);
          this.eventBus.post(attestation);
        }
      }
    } catch (InterruptedException e) {
      LOG.warn(e.toString());
    }
  }
}
