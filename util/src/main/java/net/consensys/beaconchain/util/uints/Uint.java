package net.consensys.beaconchain.util.uints;

import java.math.BigInteger;

public abstract class Uint {

    private final int nBits;
    private final BigInteger value;
    private final BigInteger modulus;

    // Constructor
    Uint(int nBits, BigInteger value) {

        assert(nBits > 0);
        BigInteger modulus = BigInteger.valueOf(2).pow(nBits);
        if (value.compareTo(modulus) >= 0 || value.signum() < 0) {
            throw new IllegalArgumentException("Failed to create a Uint with nBits = " + nBits
                    + " and value = " + value);
        }

        this.nBits = nBits;
        this.value = value;
        this.modulus = modulus;
    }

    public BigInteger getValue() {
        return value;
    }

    // In all of the following methods, the returned value will be sized according to the size of the Uint object
    // the method was called on. The size of `a` is ignored.

    protected BigInteger doAdd(Uint a) {
        return value.add(a.getValue()).mod(modulus);
    }

    protected BigInteger doSub(Uint a) {
        BigInteger x = value.subtract(a.getValue()).mod(modulus);
        return x.signum() < 0 ? x.add(modulus) : x;
    }

    protected BigInteger doMul(Uint a) {
        return value.multiply(a.getValue()).mod(modulus);
    }

    protected BigInteger doDiv(Uint a) {
        return value.divide(a.getValue());
    }

    public abstract Uint add(Uint a);

    public abstract Uint sub(Uint a);

    public abstract Uint mul(Uint a);

    public abstract Uint div(Uint a);

    public int compareTo(Uint a) {
        return value.compareTo(a.getValue());
    }

    @Override
    public String toString() {
        return value.toString();
    }

    // TODO We'll probably need a toBytes() or other serialisation method.
}
