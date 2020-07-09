package tech.pegasys.teku.bls.supra;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.p1_affine;
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
