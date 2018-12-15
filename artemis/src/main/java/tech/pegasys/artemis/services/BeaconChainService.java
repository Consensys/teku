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

package tech.pegasys.artemis.services;
import tech.pegasys.artemis.vrc.NewBlockEvent;
import tech.pegasys.artemis.vrc.ValidatorRegisteredEvent;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public class BeaconChainService implements ServiceInterface{

    private int numValidators;
    private final EventBus eventBus;

    public BeaconChainService(){
        this.numValidators = 0;
        this.eventBus = EventBusFactory.getInstance();
    }

    @Override
    public void init(){
        this.eventBus.register(this);
    }

    @Override
    public void start(){
    }

    @Override
    public void stop(){

    }

    @Subscribe
    public void onNewBlock(NewBlockEvent newBlockEvent){
        System.out.println("New Block Event detected");
        System.out.println("Block Number:" + newBlockEvent.getInfo());
    }

    @Subscribe
    public void onValidatorRegistered(ValidatorRegisteredEvent validatorRegisteredEvent){
        this.numValidators++;
        System.out.println("Validator Registration Event detected");
        System.out.println("Validator Number: " + validatorRegisteredEvent.getInfo());
    }

}
