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

package tech.pegasys.artemis.vrc;


import com.google.common.eventbus.EventBus;

// TODO:  This class needs to utilize a Web3 provider to listen to the PoWChain
// for validator registrations.
public class ValidatorRegistrationClient {

    private final EventBus eventBus;

    public ValidatorRegistrationClient(EventBus eventBus){
        this.eventBus = eventBus;
    }

    public void listenToPoWChain(){
        // TODO:  This code will be removed when we have the code
        // wired to listen for real validator registrations.
        int blockNum = 0;
        int validatorNum = 0;
        while(true){
            try {
                Thread.sleep(1000);
            }
            catch(InterruptedException e){
                System.out.println(e);
            }

             //randomly create an event
            int eventType = (int) (Math.random() * 4) + 1;
            if(eventType == 1){
                validatorNum++;
                // let listeners know that a new validator has registered
                validatorRegistered(Integer.toString(validatorNum));
            }
            else {
                blockNum++;
                // let listeners know that there is a new block
                newBlock(Integer.toString(blockNum));
            }
        }
    }

    public void validatorRegistered(String validatorInfo){
        // TODO: pass in real validator information that the beacon chain needs to know about
        ValidatorRegisteredEvent event = new ValidatorRegisteredEvent();
        event.setInfo(validatorInfo);
        this.eventBus.post(event);
    }

    public void newBlock(String blockInfo){
        // TODO: pass in real block information that the beacon chain needs to know about
        NewBlockEvent event = new NewBlockEvent();
        event.setInfo(blockInfo);
        this.eventBus.post(event);
    }


}
