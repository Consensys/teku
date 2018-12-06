package net.consensys.beaconchain.controllers;
import net.consensys.beaconchain.services.EventBusFactory;
import net.consensys.beaconchain.services.PowchainService;

public class ServiceController {
    PowchainService powchainService;
    public ServiceController(){
        this.init();
    }

    public void init(){
        // initialize/register all services

        // PoWchain Service
        this.powchainService = new PowchainService();
        EventBusFactory.getEventBus().register(this.powchainService);

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
