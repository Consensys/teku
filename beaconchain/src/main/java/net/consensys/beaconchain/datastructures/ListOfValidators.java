package net.consensys.beaconchain.datastructures;

import org.web3j.abi.datatypes.generated.Int64;

public class ListOfValidators {

    private ValidatorRecord[] validators;
    private Int64 dynasty;
    private Int64 slot;

    public ListOfValidators() {

    }

}