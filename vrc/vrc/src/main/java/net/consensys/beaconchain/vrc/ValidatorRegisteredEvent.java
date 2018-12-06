package net.consensys.beaconchain.vrc;

//TODO: This class needs to be modified to contain
// the validator info that the beacon chain needs.
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
