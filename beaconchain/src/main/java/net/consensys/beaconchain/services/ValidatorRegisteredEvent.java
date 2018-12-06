package net.consensys.beaconchain.services;

public class ValidatorRegisteredEvent {

    private String name;

    public ValidatorRegisteredEvent(){

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
