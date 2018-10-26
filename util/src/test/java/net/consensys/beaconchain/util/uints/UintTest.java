package net.consensys.beaconchain.util.uints;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class UintTest {

    @Test(expected = IllegalArgumentException.class)
    public void badUint8Test() {
        Uint8 a = new Uint8(256);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badUint256Test() {
        Uint256 a = new Uint256(BigInteger.valueOf(2).pow(256));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeUint32Test() {
        Uint32 a = new Uint32(BigInteger.valueOf(-42));
    }

    @Test
    public void UintComparisonTest() {

        Uint8 a = new Uint8(0);
        Uint8 b = new Uint8(42);
        Uint8 c = new Uint8(255);

        assertEquals(b.compareTo(a), 1);
        assertEquals(b.compareTo(b), 0);
        assertEquals(b.compareTo(c), -1);

        Uint16 d = new Uint16(42);

        assertEquals(d.compareTo(a), 1);
        assertEquals(d.compareTo(b), 0);
        assertEquals(d.compareTo(c), -1);
    }

    @Test
    public void UintArithmeticTest() {

        Uint8 a = new Uint8(255);
        Uint16 b = new Uint16(42);

        assertEquals(a.add(b).compareTo(new Uint16(41)), 0);
        assertEquals(a.sub(b).compareTo(new Uint16(213)), 0);
        assertEquals(a.mul(b).compareTo(new Uint16(214)), 0);
        assertEquals(a.div(b).compareTo(new Uint16(6)), 0);

        assertEquals(b.add(a).compareTo(new Uint16(297)), 0);
        assertEquals(b.sub(a).compareTo(new Uint16(65323)), 0);
        assertEquals(b.mul(a).compareTo(new Uint16(10710)), 0);
        assertEquals(b.div(a).compareTo(new Uint16(0)), 0);
    }

    @Test(expected = ArithmeticException.class)
    public void UintDivideByZero() {
        Uint32 a = new Uint32(42);
        Uint32 b = a.div(new Uint32(0));
    }

}
