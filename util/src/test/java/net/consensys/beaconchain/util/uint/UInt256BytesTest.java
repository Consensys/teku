package net.consensys.beaconchain.util.uint;

import net.consensys.beaconchain.util.bytes.Bytes32;
import net.consensys.beaconchain.util.bytes.MutableBytes32;
import net.consensys.beaconchain.util.uint.UInt256Bytes.BinaryLongOp;
import net.consensys.beaconchain.util.uint.UInt256Bytes.BinaryOp;

import java.math.BigInteger;

import org.junit.Assert;
import org.junit.Test;

public class UInt256BytesTest {

  private static String h(String n) {
    return UInt256.of(new BigInteger(n)).toShortHexString();
  }

  @Test
  public void shiftLeft() {
    shiftLeft("0x01", 1, "0x02");
    shiftLeft("0x01", 2, "0x04");
    shiftLeft("0x01", 8, "0x0100");
    shiftLeft("0x01", 9, "0x0200");
    shiftLeft("0x01", 16, "0x10000");

    shiftLeft("0x00FF00", 4, "0x0FF000");
    shiftLeft("0x00FF00", 8, "0xFF0000");
    shiftLeft("0x00FF00", 1, "0x01FE00");
  }

  @Test
  public void shiftRight() {
    shiftRight("0x01", 1, "0x00");
    shiftRight("0x10", 1, "0x08");
    shiftRight("0x10", 2, "0x04");
    shiftRight("0x10", 8, "0x00");

    shiftRight("0x1000", 4, "0x0100");
    shiftRight("0x1000", 5, "0x0080");
    shiftRight("0x1000", 8, "0x0010");
    shiftRight("0x1000", 9, "0x0008");
    shiftRight("0x1000", 16, "0x0000");

    shiftRight("0x00FF00", 4, "0x000FF0");
    shiftRight("0x00FF00", 8, "0x0000FF");
    shiftRight("0x00FF00", 1, "0x007F80");
  }

  @Test
  public void longModulo() {
    longModulo(h("0"), 2, h("0"));
    longModulo(h("1"), 2, h("1"));
    longModulo(h("2"), 2, h("0"));
    longModulo(h("3"), 2, h("1"));

    longModulo(h("13492324908428420834234908342"), 2, h("0"));
    longModulo(h("13492324908428420834234908343"), 2, h("1"));

    longModulo(h("0"), 8, h("0"));
    longModulo(h("1"), 8, h("1"));
    longModulo(h("2"), 8, h("2"));
    longModulo(h("3"), 8, h("3"));
    longModulo(h("7"), 8, h("7"));
    longModulo(h("8"), 8, h("0"));
    longModulo(h("9"), 8, h("1"));

    longModulo(h("1024"), 8, h("0"));
    longModulo(h("1026"), 8, h("2"));

    longModulo(h("13492324908428420834234908342"), 8, h("6"));
    longModulo(h("13492324908428420834234908343"), 8, h("7"));
    longModulo(h("13492324908428420834234908344"), 8, h("0"));
  }

  @Test
  public void divide() {
    longDivide(h("0"), 2, h("0"));
    longDivide(h("1"), 2, h("0"));
    longDivide(h("2"), 2, h("1"));
    longDivide(h("3"), 2, h("1"));
    longDivide(h("4"), 2, h("2"));

    longDivide(h("13492324908428420834234908341"), 2, h("6746162454214210417117454170"));
    longDivide(h("13492324908428420834234908342"), 2, h("6746162454214210417117454171"));
    longDivide(h("13492324908428420834234908343"), 2, h("6746162454214210417117454171"));

    longDivide(h("2"), 8, h("0"));
    longDivide(h("7"), 8, h("0"));
    longDivide(h("8"), 8, h("1"));
    longDivide(h("9"), 8, h("1"));
    longDivide(h("17"), 8, h("2"));

    longDivide(h("1024"), 8, h("128"));
    longDivide(h("1026"), 8, h("128"));

    longDivide(h("13492324908428420834234908342"), 8, h("1686540613553552604279363542"));
    longDivide(h("13492324908428420834234908342"), 2048, h("6588049271693564860466263"));
    longDivide(h("13492324908428420834234908342"), 131072, h("102938269870211950944785"));
  }

  @Test
  public void multiply() {
    longMultiply(h("0"), 2, h("0"));
    longMultiply(h("1"), 2, h("2"));
    longMultiply(h("2"), 2, h("4"));
    longMultiply(h("3"), 2, h("6"));
    longMultiply(h("4"), 2, h("8"));

    longMultiply(h("10"), 18, h("180"));

    longMultiply(h("13492324908428420834234908341"), 2, h("26984649816856841668469816682"));
    longMultiply(h("13492324908428420834234908342"), 2, h("26984649816856841668469816684"));

    longMultiply(h("2"), 8, h("16"));
    longMultiply(h("7"), 8, h("56"));
    longMultiply(h("8"), 8, h("64"));
    longMultiply(h("17"), 8, h("136"));

    longMultiply(h("13492324908428420834234908342"), 8, h("107938599267427366673879266736"));
    longMultiply(h("13492324908428420834234908342"), 2048, h("27632281412461405868513092284416"));
    longMultiply(h("13492324908428420834234908342"), 131072,
        h("1768466010397529975584837906202624"));
  }

  @Test
  public void add() {
    longAdd(h("0"), 1, h("1"));
    longAdd(h("0"), 100, h("100"));
    longAdd(h("2"), 2, h("4"));
    longAdd(h("100"), 90, h("190"));

    longAdd(h("13492324908428420834234908342"), 10, h("13492324908428420834234908352"));
    longAdd(h("13492324908428420834234908342"), 23422141424214L,
        h("13492324908428444256376332556"));

    longAdd(h("1"), Long.MAX_VALUE, h(UInt256.of(2).pow(UInt256.of(63)).toString()));

    longAdd(h("69539042617438235654073171722120479225708093440527479355806409025672010641359"), 0,
        h("69539042617438235654073171722120479225708093440527479355806409025672010641359"));
    longAdd(h("69539042617438235654073171722120479225708093440527479355806409025672010641359"), 10,
        h("69539042617438235654073171722120479225708093440527479355806409025672010641369"));
  }

  @Test
  public void subtract() {
    longSubtract(h("1"), 0, h("1"));
    longSubtract(h("100"), 0, h("100"));
    longSubtract(h("4"), 2, h("2"));
    longSubtract(h("100"), 10, h("90"));
    longSubtract(h("1"), 1, h("0"));

    longSubtract(h("69539042617438235654073171722120479225708093440527479355806409025672010641359"),
        0, h("69539042617438235654073171722120479225708093440527479355806409025672010641359"));
    longSubtract(h("69539042617438235654073171722120479225708093440527479355806409025672010641359"),
        10, h("69539042617438235654073171722120479225708093440527479355806409025672010641349"));
  }

  @Test
  public void bitLength() {
    bitLength("0x", 0);
    bitLength("0x1", 1);
    bitLength("0x2", 2);
    bitLength("0x3", 2);
    bitLength("0xF", 4);
    bitLength("0x8F", 8);

    bitLength("0x100000000", 33);
  }

  private void bitLength(String input, int expectedLength) {
    Assert.assertEquals(expectedLength,
        UInt256Bytes.bitLength(Bytes32.fromHexStringLenient(input)));
  }

  private void shiftLeft(String input, int shift, String expected) {
    Bytes32 v = Bytes32.fromHexStringLenient(input);
    intOp(UInt256Bytes::shiftLeft, v, shift, expected);
  }

  private void shiftRight(String input, int shift, String expected) {
    Bytes32 v = Bytes32.fromHexStringLenient(input);
    intOp(UInt256Bytes::shiftRight, v, shift, expected);
  }

  private void longModulo(String input, long modulo, String expected) {
    Bytes32 v = Bytes32.fromHexStringLenient(input);
    longOp(UInt256Bytes::modulo, UInt256Bytes::modulo, v, modulo, expected);
  }

  private void longDivide(String input, long divisor, String expected) {
    Bytes32 v = Bytes32.fromHexStringLenient(input);
    longOp(UInt256Bytes::divide, UInt256Bytes::divide, v, divisor, expected);
  }

  private void longMultiply(String input, long divisor, String expected) {
    Bytes32 v = Bytes32.fromHexStringLenient(input);
    longOp(UInt256Bytes::multiply, UInt256Bytes::multiply, v, divisor, expected);
  }

  private void longAdd(String v1, long v2, String expected) {
    Bytes32 v = Bytes32.fromHexStringLenient(v1);
    longOp(UInt256Bytes::add, UInt256Bytes::add, v, v2, expected);
  }

  private void longSubtract(String v1, long v2, String expected) {
    Bytes32 v = Bytes32.fromHexStringLenient(v1);
    longOp(UInt256Bytes::subtract, UInt256Bytes::subtract, v, v2, expected);
  }

  interface BinaryIntOp {
    void applyOp(Bytes32 op1, int op2, MutableBytes32 result);
  }

  private void intOp(BinaryIntOp op, Bytes32 v1, int v2, String expected) {
    intOp(op, v1, v2, Bytes32.fromHexStringLenient(expected));
  }

  private void intOp(BinaryIntOp op, Bytes32 v1, int v2, Bytes32 expected) {
    // Note: we only use this for bit-related operations, so displaying as Hex on error.

    MutableBytes32 r1 = MutableBytes32.create();
    op.applyOp(v1, v2, r1);
    assertEquals(expected, r1, true);

    // Also test in-place.
    MutableBytes32 r2 = MutableBytes32.create();
    v1.copyTo(r2);
    op.applyOp(r2, v2, r2);
    assertEquals(expected, r2, true);
  }

  private void longOp(BinaryLongOp opLong, BinaryOp op, Bytes32 v1, long v2, String expected) {
    Bytes32 result = Bytes32.fromHexStringLenient(expected);
    longOp(opLong, v1, v2, result);

    // Also test the Bytes32 variant to avoid tests duplication.
    op(op, v1, UInt256Bytes.of(v2), result);
  }

  private void longOp(BinaryLongOp op, Bytes32 v1, long v2, Bytes32 expected) {
    MutableBytes32 r1 = MutableBytes32.create();
    op.applyOp(v1, v2, r1);
    assertEquals(expected, r1, false);

    // Also test in-place.
    MutableBytes32 r2 = MutableBytes32.create();
    v1.copyTo(r2);
    op.applyOp(r2, v2, r2);
    assertEquals(expected, r2, false);
  }

  private void op(BinaryOp op, Bytes32 v1, Bytes32 v2, String expected) {
    op(op, v1, v2, Bytes32.fromHexStringLenient(expected));
  }

  private void op(BinaryOp op, Bytes32 v1, Bytes32 v2, Bytes32 expected) {
    MutableBytes32 r1 = MutableBytes32.create();
    op.applyOp(v1, v2, r1);
    assertEquals(expected, r1, false);

    // Also test in-place.
    MutableBytes32 r2 = MutableBytes32.create();
    v1.copyTo(r2);
    op.applyOp(r2, v2, r2);
    assertEquals(expected, r2, false);
  }

  private void assertEquals(Bytes32 expected, Bytes32 actual, boolean displayAsHex) {
    if (displayAsHex) {
      Assert.assertEquals(expected, actual);
    } else {
      String msg = String.format("Expected %s but got %s", UInt256Bytes.toString(expected),
          UInt256Bytes.toString(actual));
      Assert.assertEquals(msg, expected, actual);
    }
  }
}
