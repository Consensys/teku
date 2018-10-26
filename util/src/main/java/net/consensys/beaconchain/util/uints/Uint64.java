package net.consensys.beaconchain.util.uints;

import java.math.BigInteger;

// DO NOT EDIT! This file was automatically generated from Uint.template by ./UintGenerate.sh

public class Uint64 extends Uint {

    // Constructor
    public Uint64(BigInteger value) {
        super(64, value);
    }

    // Constructor
    public Uint64(long value) {
        this(BigInteger.valueOf(value));
    }

    // Constructor
    public Uint64(int value) {
        this(BigInteger.valueOf((long) value));
    }

    @Override
    public Uint64 add(Uint a) {
        return new Uint64(doAdd(a));
    }

    @Override
    public Uint64 sub(Uint a) {
        return new Uint64(doSub(a));
    }

    @Override
    public Uint64 mul(Uint a) {
        return new Uint64(doMul(a));
    }

    @Override
    public Uint64 div(Uint a) {
        return new Uint64(doDiv(a));
    }
}
