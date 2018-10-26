package net.consensys.beaconchain.util.uints;

import java.math.BigInteger;

// DO NOT EDIT! This file was automatically generated from Uint.template by ./UintGenerate.sh

public class Uint256 extends Uint {

    // Constructor
    public Uint256(BigInteger value) {
        super(256, value);
    }

    // Constructor
    public Uint256(long value) {
        this(BigInteger.valueOf(value));
    }

    // Constructor
    public Uint256(int value) {
        this(BigInteger.valueOf((long) value));
    }

    @Override
    public Uint256 add(Uint a) {
        return new Uint256(doAdd(a));
    }

    @Override
    public Uint256 sub(Uint a) {
        return new Uint256(doSub(a));
    }

    @Override
    public Uint256 mul(Uint a) {
        return new Uint256(doMul(a));
    }

    @Override
    public Uint256 div(Uint a) {
        return new Uint256(doDiv(a));
    }
}
