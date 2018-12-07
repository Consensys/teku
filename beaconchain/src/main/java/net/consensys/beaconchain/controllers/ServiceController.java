package net.consensys.beaconchain.controllers;

import com.google.common.eventbus.EventBus;
import net.consensys.beaconchain.services.EventBusFactory;
import net.consensys.beaconchain.services.PowchainService;

public class ServiceController {
    private PowchainService powchainService;
    private EventBus eventBus;

    public ServiceController(){
        this.powchainService = PowchainService.getInstance();
        this.eventBus = EventBusFactory.getInstance();
        this.init();
    }

    // initialize/register all services
    public void init(){

        // PoWchain Service
       this.eventBus.register(this.powchainService);

        // Blockchain Service

        // Validator Service

        // P2P Service

        // RPC Service
    }

    public void start(){
        // start all services
        this.powchainService.start();

    }

    public void stop(){
        // stop all services
    }
}
