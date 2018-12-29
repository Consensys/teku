/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.services.beaconchain;
import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainblocks.BeaconBlock;
import tech.pegasys.artemis.pow.event.ChainStartEvent;
import tech.pegasys.artemis.pow.event.ValidatorRegistrationEvent;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.state.BeaconState;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BeaconChainService implements ServiceInterface{

    private final EventBus eventBus;
    private BeaconState state;
    private StateTransition stateTransition;
    private ScheduledExecutorService scheduler;
    private static final Logger logger = LogManager.getLogger();

    public BeaconChainService(){
        this.eventBus = new AsyncEventBus(Executors.newCachedThreadPool());
        this.state = new BeaconState();
        this.stateTransition = new StateTransition();
    }

    @Override
    public void init(){
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.eventBus.register(this);
    }

    @Override
    public void run(){
        // slot scheduler fires an event that tells us when it is time for a new slot
        int initialDelay = 0;
        scheduler.scheduleAtFixedRate(new SlotScheduler(this.eventBus), initialDelay,
            Constants.SLOT_DURATION , TimeUnit.SECONDS);
    }

    @Override
    public void stop(){
        this.scheduler.shutdown();
        this.eventBus.unregister(this);
    }

    @Subscribe
    public void onChainStarted(ChainStartEvent event){
        logger.info("ChainStart Event Detected");
    }

    @Subscribe
    public void onValidatorRegistered(ValidatorRegistrationEvent event){
        logger.info("Validator Registration Event detected");
        //logger.info("   Validator Number: " + validatorRegisteredEvent.getInfo());
    }

    @Subscribe
    public void onNewSlot(Date date){
        logger.info("****** New Slot at: " + date + " ******");

        stateTransition.initiate(this.state, new BeaconBlock());

    }

    @Subscribe
    public void onNewBlock(BeaconBlock beaconBlock){
        logger.info("New Beacon Block Event detected");
        logger.info("   Block Number:" + beaconBlock.getSlot());
    }
}
