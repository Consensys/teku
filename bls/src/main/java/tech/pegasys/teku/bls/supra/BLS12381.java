package tech.pegasys.teku.bls.supra;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.supra.swig.BLST_ERROR;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.p2;
import tech.pegasys.teku.bls.supra.swig.p2_affine;

public class BLS12381 {

  private static String G1GeneratorCompressed =
      "0x97f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb";

  static {

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

  public static Signature aggregate(List<Signature> signatures) {
    p2 sum = new p2();
    blst.p2_from_affine(sum, signatures.get(0).ec2Point);
    for (int i = 1; i < signatures.size(); i++) {
      blst.p2_add_affine(sum, sum, signatures.get(i).ec2Point);
    }
    p2_affine res = new p2_affine();
    blst.p2_to_affine(res, sum);
    sum.delete();
    return new Signature(res);
  }
}
