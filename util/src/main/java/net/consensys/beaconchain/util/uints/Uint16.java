package net.consensys.beaconchain.util.uints;

import java.math.BigInteger;

// DO NOT EDIT! This file was automatically generated from Uint.template by ./UintGenerate.sh

public class Uint16 extends Uint {

    // Constructor
    public Uint16(BigInteger value) {
        super(16, value);
    }

    // Constructor
    public Uint16(long value) {
        this(BigInteger.valueOf(value));
    }

    // Constructor
    public Uint16(int value) {
        this(BigInteger.valueOf((long) value));
    }

    @Override
    public Uint16 add(Uint a) {
        return new Uint16(doAdd(a));
    }

    @Override
    public Uint16 sub(Uint a) {
        return new Uint16(doSub(a));
    }

    @Override
    public Uint16 mul(Uint a) {
        return new Uint16(doMul(a));
    }

    @Override
    public Uint16 div(Uint a) {
        return new Uint16(doDiv(a));
    }
}
