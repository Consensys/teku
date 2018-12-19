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
import tech.pegasys.artemis.datastructures.BeaconChainBlocks.BeaconBlock;
import tech.pegasys.artemis.factories.EventBusFactory;
import tech.pegasys.artemis.services.ServiceInterface;
import tech.pegasys.artemis.state.BeaconState;
import tech.pegasys.artemis.vrc.NewPoWBlockEvent;
import tech.pegasys.artemis.vrc.ValidatorRegisteredEvent;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public class BeaconChainService implements ServiceInterface{

    private final EventBus eventBus;
    private BeaconState state;
    private StateTransition stateTransition;

    public BeaconChainService(){
        this.eventBus = EventBusFactory.getInstance();
        this.state = new BeaconState();
        this.stateTransition = new StateTransition();
    }

    @Override
    public void init(){
        this.eventBus.register(this);
    }

    @Override
    public void start(){
        slotScheduler();
    }

    @Override
    public void stop(){

    }

    @Subscribe
    public void onNewPoWBlock(NewPoWBlockEvent newPoWBlockEvent){
        System.out.println("New POW Block Event detected");
        System.out.println("   Block Number:" + newPoWBlockEvent.getInfo());
    }

    @Subscribe
    public void onValidatorRegistered(ValidatorRegisteredEvent validatorRegisteredEvent){
        System.out.println("Validator Registration Event detected");
        System.out.println("   Validator Number: " + validatorRegisteredEvent.getInfo());
    }

    @Subscribe
    public void onNewSlot(Date date){
        System.out.println("****** New Slot at: " + date + " ******");

        stateTransition.initiate(this.state, new BeaconBlock());

    }

    @Subscribe
    public void onNewBlock(BeaconBlock beaconBlock){
        System.out.println("New Beacon Block Event detected");
        System.out.println("   Block Number:" + beaconBlock.getSlot());
    }

   // slot scheduler fires an event that tells us when it is time for a new slot
    protected void slotScheduler(){
        int intialDelay = 0;
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                EventBus eventBus = EventBusFactory.getInstance();
                eventBus.post(new Date());
            }
        }, intialDelay, Constants.SLOT_DURATION , TimeUnit.SECONDS);
    }

}
