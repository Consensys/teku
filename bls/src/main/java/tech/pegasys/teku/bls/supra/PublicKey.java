package tech.pegasys.teku.bls.supra;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.p1_affine;

public class PublicKey {
  private static final int COMPRESSED_PK_SIZE = 48;
  private static final int UNCOMPRESSED_PK_LENGTH = 49;

  public static PublicKey fromBytes(Bytes compressed) {
    checkArgument(
        compressed.size() == COMPRESSED_PK_SIZE,
        "Expected " + COMPRESSED_PK_SIZE + " bytes of input but got %s",
        compressed.size());
    p1_affine ecPoint = new p1_affine();
    blst.p1_uncompress(ecPoint, compressed.toArrayUnsafe());
    return new PublicKey(ecPoint);
  }

  public final p1_affine ecPoint;

  public PublicKey(p1_affine ecPoint) {
    this.ecPoint = ecPoint;
  }

  public Bytes toBytes() {
    byte[] res = new byte[COMPRESSED_PK_SIZE];
    blst.p1_affine_compress(res, ecPoint);
    return Bytes.wrap(res);
  }
}
