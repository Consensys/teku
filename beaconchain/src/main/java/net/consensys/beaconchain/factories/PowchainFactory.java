package net.consensys.beaconchain.services;



public class PowchainFactory {

    private static final PowchainService powchainService = new PowchainService();

    public static PowchainService getInstance() {
        return powchainService;
    }
}
