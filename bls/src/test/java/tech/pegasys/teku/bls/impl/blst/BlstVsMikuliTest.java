//package tech.pegasys.teku.bls.impl.mikuli;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import org.apache.milagro.amcl.BLS381.ECP2;
//import org.apache.tuweni.bytes.Bytes;
//import org.apache.tuweni.bytes.Bytes32;
//import org.junit.jupiter.api.Test;
//import tech.pegasys.teku.bls.impl.blst.BLS12381;
//import tech.pegasys.teku.bls.impl.blst.HashToCurve;
//import tech.pegasys.teku.bls.impl.blst.PublicKey;
//import tech.pegasys.teku.bls.impl.blst.SecretKey;
//import tech.pegasys.teku.bls.impl.blst.Signature;
//import tech.pegasys.teku.bls.impl.mikuli.G2Point;
//import tech.pegasys.teku.bls.impl.KeyPair;
//import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
//import tech.pegasys.teku.bls.impl.blst.swig.blst;
//import tech.pegasys.teku.bls.impl.blst.swig.p2;
//import tech.pegasys.teku.bls.impl.blst.swig.p2_affine;
//import tech.pegasys.teku.bls.impl.blst.swig.pairing;
//
//public class BlstVsMikuliTest {
//  @Test
//  void signVerifyCompatibilityTest() {
//    Bytes msg = Bytes32.ZERO; //.fromHexString("123456");
//    KeyPair keyPair = KeyPair.random(1);
//    tech.pegasys.teku.bls.mikuli.Signature signature = tech.pegasys.teku.bls.mikuli.BLS12381
//        .sign(keyPair.secretKey(), msg);
//    ECP2 ecp2 = tech.pegasys.teku.bls.hashToG2.HashToCurve.hashToG2(msg);
//    tech.pegasys.teku.bls.mikuli.Signature msgHash = new tech.pegasys.teku.bls.mikuli.Signature(new G2Point(ecp2));
//    boolean res = tech.pegasys.teku.bls.mikuli.BLS12381.verify(keyPair.publicKey(), msg, signature);
//    assertThat(res).isTrue();
//
//    SecretKey blstSK = SecretKey.fromBytes(keyPair.secretKey().toBytes().slice(48 - 32));
//    p2 p2 = HashToCurve.hashToG2(msg);
//    p2_affine p2Aff = new p2_affine();
//    blst.p2_to_affine(p2Aff, p2);
//    Signature blstMsgHash = new Signature(
//        p2Aff);
//    Signature blstSignature = BLS12381
//        .sign(blstSK, msg);
//
////    assertThat(blstSignature.toBytes()).isEqualTo(signature.toBytesCompressed());
//    PublicKey blstPK = PublicKey.fromBytes(keyPair.publicKey().toBytesCompressed());
//    assertThat(blstPK.toBytes()).isEqualTo(keyPair.publicKey().toBytesCompressed());
//    boolean blstRes = BLS12381.verify(blstPK, msg, blstSignature);
//    assertThat(blstRes).isTrue();
//  }
//
//  @Test
//  void testPairing() {
//
//    Bytes msg = Bytes32.ZERO; //.fromHexString("123456");
//    KeyPair keyPair = KeyPair.random(1);
//    tech.pegasys.teku.bls.mikuli.Signature signature = tech.pegasys.teku.bls.mikuli.BLS12381
//        .sign(keyPair.secretKey(), msg);
//    ECP2 ecp2 = tech.pegasys.teku.bls.hashToG2.HashToCurve.hashToG2(msg);
//    tech.pegasys.teku.bls.mikuli.Signature msgHash = new tech.pegasys.teku.bls.mikuli.Signature(new G2Point(ecp2));
//    boolean res = tech.pegasys.teku.bls.mikuli.BLS12381.verify(keyPair.publicKey(), msg, signature);
//    assertThat(res).isTrue();
//
//    SecretKey blstSK = SecretKey.fromBytes(keyPair.secretKey().toBytes().slice(48 - 32));
//    p2 p2 = HashToCurve.hashToG2(msg);
//    p2_affine p2Aff = new p2_affine();
//    blst.p2_to_affine(p2Aff, p2);
//    Signature blstMsgHash = new Signature(
//        p2Aff);
//    Signature blstSignature = BLS12381
//        .sign(blstSK, msg);
//    PublicKey blstPK = PublicKey.fromBytes(keyPair.publicKey().toBytesCompressed());
//
//    pairing ctx = new pairing();
//    blst.pairing_init(ctx);
//    BLST_ERROR error = blst.pairing_aggregate_pk_in_g1(
//        ctx,
//        blstPK.ecPoint,
//        blstSignature.ec2Point,
//        1,
//        Bytes.wrap(Bytes.wrap(msg), Bytes.wrap(new byte[1])).toArrayUnsafe(),
//        HashToCurve.ETH2_DST.toArrayUnsafe(),
//        new byte[0]);
//    blst.pairing_commit(ctx);
//    int r = blst.pairing_finalverify(ctx, null);
//
//    System.out.println(r);
//  }
//
//  @Test
//  void testHashToCurve() {
//    Bytes msg = Bytes.fromHexString("123456");
//
//    p2 p2 = new p2();
//
//    //    while (true) {
//
//    long s = System.currentTimeMillis();
//    p2_affine p2Aff = new p2_affine();
//    //      for (int i = 0; i < 1000; i++) {
//    blst.hash_to_g2(p2, msg.toArray(), HashToCurve.ETH2_DST.toArray(), new byte[0]);
//    blst.p2_to_affine(p2Aff, p2);
//    //      }
//    System.out.println((System.currentTimeMillis() - s) + " ms for blst");
//    byte[] res = new byte[96];
//    blst.p2_affine_compress(res, p2Aff);
//    Bytes resBytes = Bytes.wrap(res);
//
//    int b = blst.p2_on_curve(p2);
//    assertThat(b).isNotZero();
//
//    s = System.currentTimeMillis();
//    //      for (int i = 0; i < 1000; i++) {
//    G2Point g2Point = G2Point.hashToG2(msg);
//    Bytes resBytes1 = g2Point.toBytesCompressed();
//    //      }
//
//    assertThat(resBytes).isEqualTo(resBytes1);
//    System.out.println((System.currentTimeMillis() - s) + " ms for teku");
//    //    }
//  }
//}
