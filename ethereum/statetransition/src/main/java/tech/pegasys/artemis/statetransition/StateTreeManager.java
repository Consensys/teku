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

package tech.pegasys.artemis.statetransition;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.pow.api.ChainStartEvent;
import tech.pegasys.artemis.pow.api.ValidatorRegistrationEvent;
import tech.pegasys.artemis.storage.ChainStorage;
import tech.pegasys.artemis.storage.ChainStorageClient;

/** Class to manage the state tree and initiate state transitions */
public class StateTreeManager {

  private final EventBus eventBus;
  private StateTransition stateTransition;
  private BeaconState state;
  private ChainStorageClient storage;
  private static final Logger LOG = LogManager.getLogger();

  public StateTreeManager(EventBus eventBus) {
    this.eventBus = eventBus;
    this.stateTransition = new StateTransition();
    this.state = new BeaconState();
    this.eventBus.register(this);
    this.storage = ChainStorage.Create(ChainStorageClient.class, eventBus);
  }

  @Subscribe
  public void onChainStarted(ChainStartEvent event) {
    LOG.info("ChainStart Event Detected");
    // TODO: initial state transition logic
  }

  @Subscribe
  public void onValidatorRegistered(ValidatorRegistrationEvent event) {
    LOG.info("Validator Registration Event detected");
    // LOG.info("   Validator Number: " + validatorRegisteredEvent.getInfo());
  }

  @Subscribe
  public void onNewSlot(Date date) {
    LOG.info("****** New Slot at: " + date + " ******");
    // TODO: get canonical state
    this.state = BeaconState.deepCopy(state);
    Optional<BeaconBlock> block = this.storage.getUnprocessedBlock();
    if (block.isPresent()) {
      LOG.info("Unprocessed block retrieved.");
      try {
        stateTransition.initiate(this.state, block.get());
        this.storage.addProcessedBlock(block.get().getState_root(), block.get());
      } catch (NoSuchElementException e) {
        LOG.warn(e.toString());
      }
    } else {
      stateTransition.initiate(this.state, null);
    }
  }
}
