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
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class MockP2PNetwork implements P2PNetwork {

  private final EventBus eventBus;
  private static final Logger LOG = LogManager.getLogger(MockP2PNetwork.class.getName());

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
  public void run() {
    this.simulateNewMessages();
  }

  @Override
  public void close() {
    this.stop();
  }

  private void simulateNewMessages() {
    try {
      StateTransition stateTransition = new StateTransition();
      BeaconState state = DataStructureUtil.createInitialBeaconState();
      Bytes32 state_root = HashTreeUtil.hash_tree_root(state.toBytes());
      BeaconBlock block = BeaconBlock.createGenesis(state_root);
      Bytes32 parent_root = HashTreeUtil.hash_tree_root(block.toBytes());

      Thread.sleep(6000);
      // ArrayList<Deposit> deposits = DataStructureUtil.newDeposits(100);
      ArrayList<Deposit> deposits = new ArrayList<>();
      while (true) {

        state = BeaconState.deepCopy(state);
        block =
            DataStructureUtil.newBeaconBlock(
                state.getSlot().plus(UnsignedLong.ONE), parent_root, state_root, deposits);
        LOG.info("In MockP2PNetwork");
        stateTransition.initiate(state, block, null);
        state_root = HashTreeUtil.hash_tree_root(state.toBytes());
        block.setState_root(state_root);

        parent_root = HashTreeUtil.hash_tree_root(block.toBytes());
        this.eventBus.post(block);
        Thread.sleep(7000);
      }
    } catch (InterruptedException | StateTransitionException e) {
      LOG.warn(e.toString());
    }
  }
}
