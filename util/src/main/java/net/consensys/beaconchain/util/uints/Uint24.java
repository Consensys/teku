package net.consensys.beaconchain.util.uints;

import java.math.BigInteger;

// DO NOT EDIT! This file was automatically generated from Uint.template by ./UintGenerate.sh

public class Uint24 extends Uint {

    // Constructor
    public Uint24(BigInteger value) {
        super(24, value);
    }

    // Constructor
    public Uint24(long value) {
        this(BigInteger.valueOf(value));
    }

    // Constructor
    public Uint24(int value) {
        this(BigInteger.valueOf((long) value));
    }

    @Override
    public Uint24 add(Uint a) {
        return new Uint24(doAdd(a));
    }

    @Override
    public Uint24 sub(Uint a) {
        return new Uint24(doSub(a));
    }

    @Override
    public Uint24 mul(Uint a) {
        return new Uint24(doMul(a));
    }

    @Override
    public Uint24 div(Uint a) {
        return new Uint24(doDiv(a));
    }
}
