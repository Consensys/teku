package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.ethereum.core.Address;
import net.consensys.beaconchain.ethereum.core.Hash;
import org.web3j.abi.datatypes.generated.Int8;
import org.web3j.abi.datatypes.generated.Int16;
import org.web3j.abi.datatypes.generated.Int64;
import org.web3j.abi.datatypes.generated.Int128;
import org.web3j.abi.datatypes.generated.Int256;

public class ValidatorRecord {

    private Address withdrawal_address;
    private Hash randao_commitment;
    private Int8 status;
    private Int16 withdrawal_shard;
    private Int64 exit_slot;
    private Int128 balance;
    private Int256 pubkey;

    public ValidatorRecord() {

    }

}
