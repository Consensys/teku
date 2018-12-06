package net.consensys.beaconchain.vrc;

import java.nio.charset.Charset;
import java.util.Random;

import com.google.common.eventbus.EventBus;

public class ValidatorRegistrationClient {

    private final EventBus eventBus;

    public ValidatorRegistrationClient(EventBus eventBus){
        this.eventBus = eventBus;
    }

    public void listenForValidators(){
        while(true){
            try {
                Thread.sleep(1000);
            }
            catch(InterruptedException e){
                System.out.println(e);
            }

            byte[] array = new byte[7];
            new Random().nextBytes(array);
            String generatedString = new String(array, Charset.forName("UTF-8"));
            validatorRegistered(generatedString);
        }
    }

    public void validatorRegistered(String validatorInfo){
        ValidatorRegisteredEvent vrcEvent = new ValidatorRegisteredEvent();
        vrcEvent.setName(validatorInfo);
        this.eventBus.post(vrcEvent);
    }


}
