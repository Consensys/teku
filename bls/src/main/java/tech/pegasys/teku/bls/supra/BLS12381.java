package tech.pegasys.teku.bls.supra;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.supra.swig.BLST_ERROR;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.p2;
import tech.pegasys.teku.bls.supra.swig.p2_affine;
import tech.pegasys.teku.bls.supra.swig.pairing;

public class BLS12381 {

  private static String G1GeneratorCompressed =
      "0x97f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb";

  private static final int BATCH_RANDOM_BYTES = 8;

  private static Random getRND() {
    // Milagro RAND has some issues with generating 'small' random numbers
    // and is not thread safe
    // Using non-secure random due to the JDK Linux secure random issue:
    // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6521844
    // A potential attack here has a very limited application and is not feasible
    // Thus using non-secure random doesn't significantly mitigate the security
    return ThreadLocalRandom.current();
  }

  public static Signature sign(SecretKey secretKey, Bytes message) {
    p2 p2Signature = new p2();
    p2 hash = HashToCurve.hashToG2(message);
    blst.sign_pk_in_g1(p2Signature, hash, secretKey.scalarVal);
    p2_affine p2SignatureAffine = new p2_affine();
    blst.p2_to_affine(p2SignatureAffine, p2Signature);
    p2Signature.delete();
    hash.delete();
    return new Signature(p2SignatureAffine);
  }

  public static boolean verify(PublicKey publicKey, Bytes message, Signature signature) {
    BLST_ERROR res = blst.core_verify_pk_in_g1(
        publicKey.ecPoint,
        signature.ec2Point,
        1,
        message.toArrayUnsafe(),
        HashToCurve.ETH2_DST.toArrayUnsafe(),
        new byte[0]);
    return res == BLST_ERROR.BLST_SUCCESS;
  }

  public static BatchSemiAggregate prepareBatchVerify(
      int index, List<PublicKey> publicKeys, Bytes message, Signature signature) {

    PublicKey aggrPubKey = PublicKey.aggregate(publicKeys);
    p2 p2 = HashToCurve.hashToG2(message);
    p2_affine p2Affine = new p2_affine();
    blst.p2_to_affine(p2Affine, p2);

    pairing ctx = new pairing();
    try {
      blst.pairing_init(ctx);
      BLST_ERROR ret = blst.pairing_mul_n_aggregate_pk_in_g1(
          ctx,
          aggrPubKey.ecPoint,
          signature.ec2Point,
          p2Affine,
          nextBatchRandomMultiplier(),
          BATCH_RANDOM_BYTES * 8);
      if (ret != BLST_ERROR.BLST_SUCCESS) throw new IllegalArgumentException("Error: " + ret);
    } catch (Exception e) {
      ctx.delete();
      throw e;
    } finally {
      p2.delete();
      p2Affine.delete(); // not sure if its copied inside pairing_mul_n_aggregate_pk_in_g1
    }
    blst.pairing_commit(ctx);

    return new BatchSemiAggregate(ctx);
  }

  public static boolean completeBatchVerify(List<BatchSemiAggregate> preparedList) {
    if (preparedList.isEmpty()) {
      return true;
    }
    pairing ctx0 = preparedList.get(0).getCtx();
    boolean mergeRes = true;
    for (int i = 1; i < preparedList.size(); i++) {
      BLST_ERROR ret = blst.pairing_merge(ctx0, preparedList.get(i).getCtx());
      mergeRes &= ret == BLST_ERROR.BLST_SUCCESS;
      preparedList.get(i).release();
    }

    int boolRes = blst.pairing_finalverify(ctx0, null);
    preparedList.get(0).release();
    return mergeRes && boolRes != 0;
  }

    private static BigInteger nextBatchRandomMultiplier() {
    byte[] scalarBytes = new byte[BATCH_RANDOM_BYTES];
    getRND().nextBytes(scalarBytes);
    return new BigInteger(1, scalarBytes);
  }


  public static final class BatchSemiAggregate {
    private final pairing ctx;
    private boolean released = false;

    private BatchSemiAggregate(pairing ctx) {
      this.ctx = ctx;
    }

    private pairing getCtx() {
      return ctx;
    }

    private void release() {
      if (released)
        throw new IllegalStateException("Attempting to use disposed BatchSemiAggregate");
      released = true;
      ctx.delete();
    }
  }
}
