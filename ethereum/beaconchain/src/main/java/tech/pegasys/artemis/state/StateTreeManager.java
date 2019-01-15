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

package tech.pegasys.artemis.state;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.beaconchainblocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.beaconchainoperations.Attestation;

public class StateTreeManager {

  private final EventBus eventBus;
  private StateTransition stateTransition;
  private BeaconState state;
  private LinkedBlockingQueue<BeaconBlock> unprocessedBlocks;
  private LinkedBlockingQueue<Attestation> unprocessedAttestations;
  private static final Logger LOG = LogManager.getLogger();

  public StateTreeManager(EventBus eventBus) {
    this.eventBus = eventBus;
    this.stateTransition = new StateTransition();
    this.state = new BeaconState();
    this.unprocessedBlocks = new LinkedBlockingQueue<BeaconBlock>();
    this.unprocessedAttestations = new LinkedBlockingQueue<Attestation>();

    this.eventBus.register(this);
  }

  @Subscribe
  public void onNewSlot(Date date) {
    LOG.info("****** New Slot at: " + date + " ******");
    Optional<BeaconBlock> block = unprocessedBlocks.stream().findFirst();
    if (block.isPresent()) {
      try {
        stateTransition.initiate(this.state, block.get());
      } catch (NoSuchElementException e) {
        LOG.warn(e.toString());
      }
    } else {
      stateTransition.initiate(this.state, null);
    }
  }

  @Subscribe
  public void onNewBlock(BeaconBlock block) {
    LOG.info("New Beacon Block Event detected");
    block.setSlot(this.state.getSlot());
    unprocessedBlocks.add(block);
  }

  @Subscribe
  public void onNewAttestation(Attestation attestation) {
    LOG.info("New Attestation Event detected");
    unprocessedAttestations.add(attestation);
  }
}
