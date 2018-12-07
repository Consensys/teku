package net.consensys.beaconchain.vrc;

import java.nio.charset.Charset;
import java.util.Random;

import com.google.common.eventbus.EventBus;

// TODO:  This class needs to utilize a Web3 provider to listen to the PoWChain
// for validator registrations.
public class ValidatorRegistrationClient {

    private final EventBus eventBus;

    public ValidatorRegistrationClient(EventBus eventBus){
        this.eventBus = eventBus;
    }

    public void listenForValidators(){
        // TODO:  This code will be removed when we have the code
        // wired to listen for real validator registrations.
        while(true){
            try {
                Thread.sleep(1000);
            }
            catch(InterruptedException e){
                System.out.println(e);
            }

            byte[] array = new byte[7];
            new Random().nextBytes(array);
            String generatedString = new String(array, Charset.forName("US-ASCII"));

            // let listeners know that a new validator has registered
            validatorRegistered(generatedString);
        }
    }

    public void validatorRegistered(String validatorInfo){
        // TODO: pass in real validator information that the beacon chain needs to know about
        ValidatorRegisteredEvent vrcEvent = new ValidatorRegisteredEvent();
        vrcEvent.setName(validatorInfo);
        this.eventBus.post(vrcEvent);
    }


}
