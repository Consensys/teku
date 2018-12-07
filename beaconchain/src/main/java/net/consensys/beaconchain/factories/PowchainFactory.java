package net.consensys.beaconchain.services;

import net.consensys.beaconchain.services.PowchainService;

public class PowchainFactory {

    private static final PowchainService powchainService = new PowchainService();

    public static PowchainService getInstance() {
        return powchainService;
    }
}
