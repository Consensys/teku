package tech.pegasys.teku.bls.supra;

import java.io.File;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.fp;
import tech.pegasys.teku.bls.supra.swig.p1;
import tech.pegasys.teku.bls.supra.swig.scalar;

public class BlstTest {

  @BeforeAll
  static void setup() {
    System.out.println(new File(".").getAbsolutePath());
    System.setProperty(
        "java.library.path",
        System.getProperty("java.library.path")
            + ";"
            + "./src/main/resources");
    System.loadLibrary("jblst");
  }

  @Test
  void test1() {
    scalar scalar = new scalar();
    long[] longs = {0x111111111111L, 0x222222222222L, 3, 4};
    scalar.setL(longs);
    long[] l = scalar.getL();
    System.out.println(Arrays.toString(l));
  }

  @Test
  void test2() {
    Bytes x =
        Bytes.fromHexString(
            "187db8f7b715c7672615292c7924d618e6f2e0026b85be837b9f8a3ba2f87160705451ceb3403df02a31bfdde5edac5e");
    Bytes y =
        Bytes.fromHexString(
            "0b08bb210c0b17f86e21eda0bd01a435259ff05f6d5d7256db66de2245c84de68030941c637d290f9e890c4aca4d9016");
    Bytes z =
        Bytes.fromHexString(
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001");
//    Bytes x =
//        Bytes.fromHexString(
//            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
//    Bytes y =
//        Bytes.fromHexString(
//            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
//    Bytes z =
//        Bytes.fromHexString(
//            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    fp fpx = new fp();
    long[] longs = toLongsB48(x);
    fpx.setL(longs);
    p1 p1_1 = new p1();
    p1_1.setX(fpx);
    fp fpy = new fp();
    fpy.setL(toLongsB48(y));
    p1_1.setY(fpx);
    fp fpz = new fp();
    fpz.setL(toLongsB48(z));
    p1_1.setZ(fpx);

    p1 res = new p1();
    blst.p1_double(res, p1_1);

    Bytes rx = fromLongsB48(p1_1.getX().getL());
    Bytes ry = fromLongsB48(p1_1.getY().getL());
    Bytes rz = fromLongsB48(p1_1.getZ().getL());

    System.out.println(blst.p1_on_curve(p1_1));
  }

  static long[] toLongsB48(Bytes b) {
    return new long[] {
        b.slice(0, 8).toLong(),
        b.slice(8, 8).toLong(),
        b.slice(16, 8).toLong(),
        b.slice(24, 8).toLong(),
        b.slice(32, 8).toLong(),
        b.slice(40, 8).toLong(),
    };
  }

  static Bytes fromLongsB48(long[] b) {
    return Bytes.wrap(
        Bytes.ofUnsignedLong(b[0]),
        Bytes.ofUnsignedLong(b[1]),
        Bytes.ofUnsignedLong(b[2]),
        Bytes.ofUnsignedLong(b[3]),
        Bytes.ofUnsignedLong(b[4]),
        Bytes.ofUnsignedLong(b[5]));
  }
}
