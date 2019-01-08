package tech.pegasys.artemis.services.beaconchain;

public class StateTransitionException extends Exception{
    public StateTransitionException(String message){
        super(message);
    }
}
