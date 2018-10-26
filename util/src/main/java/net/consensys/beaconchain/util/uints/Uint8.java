package net.consensys.beaconchain.util.uints;

import java.math.BigInteger;

// DO NOT EDIT! This file was automatically generated from Uint.template by ./UintGenerate.sh

public class Uint8 extends Uint {

    // Constructor
    public Uint8(BigInteger value) {
        super(8, value);
    }

    // Constructor
    public Uint8(long value) {
        this(BigInteger.valueOf(value));
    }

    // Constructor
    public Uint8(int value) {
        this(BigInteger.valueOf((long) value));
    }

    @Override
    public Uint8 add(Uint a) {
        return new Uint8(doAdd(a));
    }

    @Override
    public Uint8 sub(Uint a) {
        return new Uint8(doSub(a));
    }

    @Override
    public Uint8 mul(Uint a) {
        return new Uint8(doMul(a));
    }

    @Override
    public Uint8 div(Uint a) {
        return new Uint8(doDiv(a));
    }
}
