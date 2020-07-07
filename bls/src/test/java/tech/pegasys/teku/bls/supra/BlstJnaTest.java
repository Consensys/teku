package tech.pegasys.teku.bls.supra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.mikuli.G2Point;
import tech.pegasys.teku.bls.supra.jna.BlstLibrary;
import tech.pegasys.teku.bls.supra.jna.BlstLibrary.blst_fp;
import tech.pegasys.teku.bls.supra.jna.BlstLibrary.blst_fp2;
import tech.pegasys.teku.bls.supra.jna.BlstLibrary.blst_p1;
import tech.pegasys.teku.bls.supra.jna.BlstLibrary.blst_p2;
import tech.pegasys.teku.bls.supra.jna.BlstLibrary.blst_p2_affine;
import tech.pegasys.teku.bls.supra.jna.BlstLibrary.blst_scalar;
import tech.pegasys.teku.bls.supra.jna.NativeSize;

public class BlstJnaTest {

  private static final Bytes ETH2_DST =
      Bytes.wrap("BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_POP_".getBytes(StandardCharsets.US_ASCII));

  @Test
  void test4() {
    Bytes bbx0 =
        Bytes.fromHexString(
            "0x14a66e8ed818459a1378980ed6a2b4e3b0fa352b9cd55a9c903e1f39fdf936f21c8c68b6d3ee26012bd530cbc773ef06");
    Bytes bbx1 =
        Bytes.fromHexString(
            "0x0cd4305823135cf1036684b5f3bd59f07d8ff2953023bbd61c660f5e582a3da55bf3375bd743bf3e68bb9be9dcc709c2");
    Bytes bby0 =
        Bytes.fromHexString(
            "0x03b44b618aea1d66ef29bb89d88d48564ef02a0fa75762b0fb942809f9c29e8711e529d86554e70586ea2287ff7147f2");
    Bytes bby1 =
        Bytes.fromHexString(
            "0x104254bcd0dbe0f891ae4c0f95dc6aeae729fc96d9c962fd7843958f27dc5dd12d31c2c2d08140563dac7051e84eb496");
    Bytes bbz0 =
        Bytes.fromHexString(
            "0x10b8a56f2ebf4aea98e36237003805f86d3b6d72d7c1beab5fb1b5b0e26bade847f97c536fa8f09a5850ec95c7eb0e32");
    Bytes bbz1 =
        Bytes.fromHexString(
            "0x01d74eb7bccc8d375dc31b375424f7fbe61b8586e92e5158281c13979791b28a1d543de680c3ac99a5d01092a285666f");

    blst_p2 p2 = new blst_p2(
        new blst_fp2(bytesToFp(bbx0), bytesToFp(bbx1)),
        new blst_fp2(bytesToFp(bby0), bytesToFp(bby1)),
        new blst_fp2(bytesToFp(bbz0), bytesToFp(bbz1))
    );

    boolean b = BlstLibrary.INSTANCE.blst_p2_on_curve(p2);

    assertThat(b).isTrue();
  }


  @Test
  void testHashToCurve() {
    Bytes msg = Bytes.fromHexString("123456");

    blst_p2 p2 = new blst_p2();

    while (true) {

      long s = System.currentTimeMillis();
      blst_p2_affine p2Aff = new blst_p2_affine();
      for (int i = 0; i < 1000; i++) {
        BlstLibrary.INSTANCE.blst_hash_to_g2(
            p2,
            msg.toArray(),
            new NativeSize(msg.size()),
            ETH2_DST.toArray(),
            new NativeSize(ETH2_DST.size()),
            new byte[0],
            new NativeSize(0));
        BlstLibrary.INSTANCE.blst_p2_to_affine(p2Aff, p2);
      }
      System.out.println((System.currentTimeMillis() - s) + " ms for blst");


      Bytes bbx0 = fpToBytes(p2Aff.x.fp_0);
      Bytes bbx1 = fpToBytes(p2Aff.x.fp_1);
      Bytes bby0 = fpToBytes(p2Aff.y.fp_0);
      Bytes bby1 = fpToBytes(p2Aff.y.fp_1);
      Bytes bbz0 = fpToBytes(p2.z.fp_0);
      Bytes bbz1 = fpToBytes(p2.z.fp_1);

      boolean b = BlstLibrary.INSTANCE.blst_p2_on_curve(p2);

      s = System.currentTimeMillis();
      for (int i = 0; i < 1000; i++) {
        G2Point g2Point = G2Point.hashToG2(msg);
      }
      System.out.println((System.currentTimeMillis() - s) + " ms for teku");
    }
  }

  static blst_fp bytesToFp(Bytes bytes) {
    blst_fp fp_1 = new blst_fp();
    BlstLibrary.INSTANCE.blst_fp_from_bendian(fp_1, bytes.toArray());
    return fp_1;
  }

  static Bytes fpToBytes(blst_fp fp) {
    ByteBuffer byteBuffer = ByteBuffer.allocate(48);
    BlstLibrary.INSTANCE.blst_bendian_from_fp(byteBuffer, fp);
    byte[] array = byteBuffer.array();
    return Bytes.wrap(array);
  }

  @Test
  void test3() {
    Bytes fpBytes_1 =
        Bytes.fromHexString(
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002");
    Bytes fpBytes_2 =
        Bytes.fromHexString(
            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003");

    blst_fp fp_1 = new blst_fp();
    BlstLibrary.INSTANCE.blst_fp_from_bendian(fp_1, fpBytes_1.toArray());
    blst_fp fp_2 = new blst_fp();
    BlstLibrary.INSTANCE.blst_fp_from_bendian(fp_2, fpBytes_2.toArray());
    blst_fp fp_res = new blst_fp();

    BlstLibrary.INSTANCE.blst_fp_mul(fp_res, fp_1, fp_2);
    ByteBuffer byteBuffer = ByteBuffer.allocate(48);
    BlstLibrary.INSTANCE.blst_bendian_from_fp(byteBuffer, fp_res);
    byte[] array = byteBuffer.array();
    Bytes resBytes = Bytes.wrap(array);

    System.out.println(Bytes.wrap(fp_1.toBytes()));
    System.out.println(Bytes.wrap(fp_2.toBytes()));
    System.out.println(Bytes.wrap(fp_res.toBytes()));
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

    blst_fp fpX = new blst_fp();
    BlstLibrary.INSTANCE.blst_fp_from_bendian(fpX, x.toArray());
    blst_fp fpY = new blst_fp();
    BlstLibrary.INSTANCE.blst_fp_from_bendian(fpY, y.toArray());
    blst_fp fpZ = new blst_fp();
    BlstLibrary.INSTANCE.blst_fp_from_bendian(fpZ, z.toArray());

    blst_p1 p1 = new blst_p1(fpX, fpY, fpZ);
    blst_p1 res = new blst_p1();

    boolean onCurve = BlstLibrary.INSTANCE.blst_p1_on_curve(p1);

    BlstLibrary.INSTANCE.blst_p1_double(res, p1);

    System.out.println(res);
  }
  @Test
  void test1() {
    blst_scalar blst_scalar = new blst_scalar();
    int[] ints = {0x11111111, 0x22222222, 0x33333333, 0x44444444, 0x55555555, 0x66666666, 0x77777777, 0x88888888};
    BlstLibrary.INSTANCE.blst_scalar_from_uint32(blst_scalar, ints);
    blst_scalar.clear();
    System.out.println(blst_scalar);
  }
}
