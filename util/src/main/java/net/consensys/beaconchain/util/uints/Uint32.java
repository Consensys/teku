package net.consensys.beaconchain.util.uints;

import java.math.BigInteger;

// DO NOT EDIT! This file was automatically generated from Uint.template by ./UintGenerate.sh

public class Uint32 extends Uint {

    // Constructor
    public Uint32(BigInteger value) {
        super(32, value);
    }

    // Constructor
    public Uint32(long value) {
        this(BigInteger.valueOf(value));
    }

    // Constructor
    public Uint32(int value) {
        this(BigInteger.valueOf((long) value));
    }

    @Override
    public Uint32 add(Uint a) {
        return new Uint32(doAdd(a));
    }

    @Override
    public Uint32 sub(Uint a) {
        return new Uint32(doSub(a));
    }

    @Override
    public Uint32 mul(Uint a) {
        return new Uint32(doMul(a));
    }

    @Override
    public Uint32 div(Uint a) {
        return new Uint32(doDiv(a));
    }
}
