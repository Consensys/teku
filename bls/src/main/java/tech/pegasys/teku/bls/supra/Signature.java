package tech.pegasys.teku.bls.supra;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.p1_affine;
import tech.pegasys.teku.bls.supra.swig.p2;
import tech.pegasys.teku.bls.supra.swig.p2_affine;

public class Signature {
  private static final int COMPRESSED_SIG_SIZE = 96;
  private static final int UNCOMPRESSED_SIG_SIZE = 192;

  public static PublicKey fromBytes(Bytes compressed) {
    checkArgument(
        compressed.size() == COMPRESSED_SIG_SIZE,
        "Expected " + COMPRESSED_SIG_SIZE + " bytes of input but got %s",
        compressed.size());
    p1_affine ecPoint = new p1_affine();
    blst.p1_uncompress(ecPoint, compressed.toArrayUnsafe());
    return new PublicKey(ecPoint);
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

  public final p2_affine ec2Point;

  public Signature(p2_affine ec2Point) {
    this.ec2Point = ec2Point;
  }

  public Bytes toBytes() {
    byte[] res = new byte[96];
    blst.p2_affine_compress(res, ec2Point);
    return Bytes.wrap(res);
  }
}
