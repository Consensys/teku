package net.consensys.beaconchain.datastructures;

/*
https://github.com/ethereum/beacon_chain/tree/master/ssz

int8: 5 --> b’\x05’
bytes: b'cow' --> b'\x00\x00\x00\x03cow'
address: b'\x35'*20 --> b’55555555555555555555’
hash32: b'\x35'*32 --> b’55555555555555555555555555555555’
list['int8']: [3, 4, 5] --> b'\x00\x00\x00\x03\x03\x04\x05'

*/

public enum SerialTypes {
    int8, bytes, address, hash32
}
