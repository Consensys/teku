package tech.pegasys.teku.bls.supra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.hashToG2.HashToCurve;
import tech.pegasys.teku.bls.mikuli.BLS12381;
import tech.pegasys.teku.bls.mikuli.G2Point;
import tech.pegasys.teku.bls.mikuli.KeyPair;
import tech.pegasys.teku.bls.mikuli.Signature;
import tech.pegasys.teku.bls.supra.swig.BLST_ERROR;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.p1_affine;
import tech.pegasys.teku.bls.supra.swig.p2;
import tech.pegasys.teku.bls.supra.swig.p2_affine;
import tech.pegasys.teku.bls.supra.swig.pairing;

public class BlstTest {
  private static final Bytes ETH2_DST =
      Bytes.wrap("BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_POP_".getBytes(StandardCharsets.US_ASCII));

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
//    scalar scalar = new scalar();
//    long[] longs = {0x111111111111L, 0x222222222222L, 3, 4};
//    scalar.setL(longs);
//    long[] l = scalar.getL();
//    System.out.println(Arrays.toString(l));
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
    BLSPublicKey publicKey = BLSPublicKey.random(1);
    Bytes pkCompressedBytes = publicKey.toBytesCompressed();

    p1_affine p1_affine = new p1_affine();
    BLST_ERROR error = blst.p1_uncompress(p1_affine, pkCompressedBytes.toArrayUnsafe());

    int ret = blst.p1_affine_on_curve(p1_affine);

    System.out.println(ret);
//    Bytes x =
//        Bytes.fromHexString(
//            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
//    Bytes y =
//        Bytes.fromHexString(
//            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
//    Bytes z =
//        Bytes.fromHexString(
//            "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
//    fp fpx = new fp();
//    long[] longs = toLongsB48(x);
//    fpx.setL(longs);
//    p1 p1_1 = new p1();
//    p1_1.setX(fpx);
//    fp fpy = new fp();
//    fpy.setL(toLongsB48(y));
//    p1_1.setY(fpx);
//    fp fpz = new fp();
//    fpz.setL(toLongsB48(z));
//    p1_1.setZ(fpx);
//
//    p1 res = new p1();
//    blst.p1_double(res, p1_1);
//
//    Bytes rx = fromLongsB48(p1_1.getX().getL());
//    Bytes ry = fromLongsB48(p1_1.getY().getL());
//    Bytes rz = fromLongsB48(p1_1.getZ().getL());
//
//    System.out.println(blst.p1_on_curve(p1_1));
  }

  @Test
  void testHashToCurve() {
    Bytes msg = Bytes.fromHexString("123456");

    p2 p2 = new p2();

    //    while (true) {

    long s = System.currentTimeMillis();
    p2_affine p2Aff = new p2_affine();
    //      for (int i = 0; i < 1000; i++) {
    blst.hash_to_g2(p2, msg.toArray(), ETH2_DST.toArray(), new byte[0]);
    blst.p2_to_affine(p2Aff, p2);
    //      }
    System.out.println((System.currentTimeMillis() - s) + " ms for blst");
    byte[] res = new byte[96];
    blst.p2_affine_compress(res, p2Aff);
    Bytes resBytes = Bytes.wrap(res);

    int b = blst.p2_on_curve(p2);
    assertThat(b).isNotZero();

    s = System.currentTimeMillis();
    //      for (int i = 0; i < 1000; i++) {
    G2Point g2Point = G2Point.hashToG2(msg);
    Bytes resBytes1 = g2Point.toBytesCompressed();
    //      }

    assertThat(resBytes).isEqualTo(resBytes1);
    System.out.println((System.currentTimeMillis() - s) + " ms for teku");
    //    }
  }

  @Test
  void signVerifyCompatibilityTest() {
    Bytes msg = Bytes32.ZERO; //.fromHexString("123456");
    KeyPair keyPair = KeyPair.random(1);
    Signature signature = BLS12381.sign(keyPair.secretKey(), msg);
    ECP2 ecp2 = HashToCurve.hashToG2(msg);
    Signature msgHash = new Signature(new G2Point(ecp2));
    boolean res = BLS12381.verify(keyPair.publicKey(), msg, signature);
    assertThat(res).isTrue();

    SecretKey blstSK = SecretKey.fromBytes(keyPair.secretKey().toBytes().slice(48 - 32));
    p2 p2 = tech.pegasys.teku.bls.supra.HashToCurve.hashToG2(msg);
    p2_affine p2Aff = new p2_affine();
    blst.p2_to_affine(p2Aff, p2);
    tech.pegasys.teku.bls.supra.Signature blstMsgHash = new tech.pegasys.teku.bls.supra.Signature(
        p2Aff);
    tech.pegasys.teku.bls.supra.Signature blstSignature = tech.pegasys.teku.bls.supra.BLS12381
        .sign(blstSK, msg);

//    assertThat(blstSignature.toBytes()).isEqualTo(signature.toBytesCompressed());
    PublicKey blstPK = PublicKey.fromBytes(keyPair.publicKey().toBytesCompressed());
    assertThat(blstPK.toBytes()).isEqualTo(keyPair.publicKey().toBytesCompressed());
    boolean blstRes = tech.pegasys.teku.bls.supra.BLS12381.verify(blstPK, msg, blstSignature);
    assertThat(blstRes).isTrue();
  }

  @Test
  void testPairing() {

    Bytes msg = Bytes32.ZERO; //.fromHexString("123456");
    KeyPair keyPair = KeyPair.random(1);
    Signature signature = BLS12381.sign(keyPair.secretKey(), msg);
    ECP2 ecp2 = HashToCurve.hashToG2(msg);
    Signature msgHash = new Signature(new G2Point(ecp2));
    boolean res = BLS12381.verify(keyPair.publicKey(), msg, signature);
    assertThat(res).isTrue();

    SecretKey blstSK = SecretKey.fromBytes(keyPair.secretKey().toBytes().slice(48 - 32));
    p2 p2 = tech.pegasys.teku.bls.supra.HashToCurve.hashToG2(msg);
    p2_affine p2Aff = new p2_affine();
    blst.p2_to_affine(p2Aff, p2);
    tech.pegasys.teku.bls.supra.Signature blstMsgHash = new tech.pegasys.teku.bls.supra.Signature(
        p2Aff);
    tech.pegasys.teku.bls.supra.Signature blstSignature = tech.pegasys.teku.bls.supra.BLS12381
        .sign(blstSK, msg);
    PublicKey blstPK = PublicKey.fromBytes(keyPair.publicKey().toBytesCompressed());

    pairing ctx = new pairing();
    blst.pairing_init(ctx);
    BLST_ERROR error = blst.pairing_aggregate_pk_in_g1(
        ctx,
        blstPK.ecPoint,
        blstSignature.ec2Point,
        1,
        Bytes.wrap(Bytes.wrap(msg), Bytes.wrap(new byte[1])).toArrayUnsafe(),
        tech.pegasys.teku.bls.supra.HashToCurve.ETH2_DST.toArrayUnsafe(),
        new byte[0]);
    blst.pairing_commit(ctx);
    int r = blst.pairing_finalverify(ctx, null);

    System.out.println(r);
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
