package tech.pegasys.teku.bls.impl.blst;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.security.SecureRandom;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.impl.blst.BlstBLS12381.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p1_affine;

public class BlstTest {
  private static final SecureRandom random = new SecureRandom(new byte[] {1});

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
  }

  @Test
  void testBatchVerifySingleSig() {
    Bytes msg = Bytes32.ZERO; //.fromHexString("123456");

    BlstSecretKey blstSK = BlstSecretKey.generateNew(random);
    BlstPublicKey blstPK = blstSK.toPublicKey();

    BlstSignature blstSignature = BlstBLS12381.sign(blstSK, msg);

    BatchSemiAggregate semiAggregate =
        BlstBLS12381.prepareBatchVerify(0, List.of(blstPK), msg, blstSignature);

    boolean blstRes = BlstBLS12381.completeBatchVerify(List.of(semiAggregate));
    assertThat(blstRes).isTrue();
  }

  @Test
  void testBatchVerifyCoupleSigs() {
    Bytes msg1 = Bytes32.fromHexString("123456");

    BlstSecretKey blstSK1 = BlstSecretKey.generateNew(random);
    BlstPublicKey blstPK1 = blstSK1.toPublicKey();
    BlstSignature blstSignature1 = BlstBLS12381.sign(blstSK1, msg1);

    Bytes msg2 = Bytes32.fromHexString("654321");

    BlstSecretKey blstSK2 = BlstSecretKey.generateNew(random);
    BlstPublicKey blstPK2 = blstSK2.toPublicKey();
    BlstSignature blstSignature2 = BlstBLS12381.sign(blstSK2, msg2);

    BatchSemiAggregate semiAggregate1 =
        BlstBLS12381.prepareBatchVerify(0, List.of(blstPK1), msg1, blstSignature1);
    BatchSemiAggregate semiAggregate2 =
        BlstBLS12381.prepareBatchVerify(1, List.of(blstPK2), msg2, blstSignature2);

    boolean blstRes = BlstBLS12381.completeBatchVerify(List.of(semiAggregate1, semiAggregate2));
    assertThat(blstRes).isTrue();
  }
}
