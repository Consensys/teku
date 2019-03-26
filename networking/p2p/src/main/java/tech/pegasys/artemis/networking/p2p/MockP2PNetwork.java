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
import com.google.common.eventbus.Subscribe;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.data.adapter.TimeSeriesAdapter;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.util.alogger.ALogger;

public class MockP2PNetwork implements P2PNetwork {

  private final EventBus eventBus;
  private TimeSeriesRecord chainData;
  private static final ALogger LOG = new ALogger(MockP2PNetwork.class.getName());
  private boolean printEnabled = false;

  public MockP2PNetwork(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
    this.chainData = new TimeSeriesRecord();
  }

  public MockP2PNetwork(EventBus eventBus, boolean printEnabled) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
    this.printEnabled = printEnabled;
    this.chainData = new TimeSeriesRecord();
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
   * Connects to a {@link String Peer}.
   *
   * @param peer Peer to connect to.
   * @return Future of the established {}
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
  public void run() {}

  @Override
  public void close() {
    this.stop();
  }

  @Subscribe
  public void onNewUnprocessedBlock(BeaconBlock block) {
    // now send it out into the p2p world
    LOG.log(
        Level.INFO,
        "Beacon Block you are free to congest the p2p network: "
            + block.getState_root().toHexString());
  }

  @Override
  public synchronized void onDataEvent(RawRecord record) {
    TimeSeriesAdapter adapter = new TimeSeriesAdapter(record);
    chainData = adapter.transform();
  }
}
