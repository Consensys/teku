package net.consensys.beaconchain.services;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public class BeaconChainService {

    public BeaconChainService(){
        EventBus eventBus = EventBusFactory.getEventBus();
    }

    @Subscribe
    public void onValidatorRegistered(ValidatorRegisteredEvent validatorRegisteredEvent){

    }

}
