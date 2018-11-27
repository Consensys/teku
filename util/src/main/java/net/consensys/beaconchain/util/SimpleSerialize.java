package net.consensys.beaconchain.util;

import net.consensys.beaconchain.util.bytes.Bytes32;

public class SimpleSerialize {

    public static byte[] serialize(Bytes32 bytes32){
        return bytes32.extractArray();
    }
    public static Bytes32 deserialize(byte[] bytes){
        return Bytes32.wrap(bytes);
    }
}
