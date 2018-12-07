package net.consensys.beaconchain.services;
import net.consensys.beaconchain.vrc.ValidatorRegisteredEvent;
import net.consensys.beaconchain.vrc.ValidatorRegistrationClient;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public class PowchainService {

    private int numValidators;
    private final ValidatorRegistrationClient vrc;

    public PowchainService(){
        EventBus eventBus = EventBusFactory.getInstance();
        this.vrc = new ValidatorRegistrationClient(eventBus);
        this.numValidators = 0;
    }

    @Subscribe
    public void onValidatorRegistered(ValidatorRegisteredEvent validatorRegisteredEvent){
        this.numValidators++;
        System.out.println(validatorRegisteredEvent.getName());
        System.out.println("Number of validators registered: " + this.numValidators);
    }

    public void start(){
        this.vrc.listenForValidators();
    }

}
