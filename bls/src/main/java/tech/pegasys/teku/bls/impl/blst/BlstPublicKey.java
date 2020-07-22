package tech.pegasys.teku.bls.impl.blst;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p1;
import tech.pegasys.teku.bls.impl.blst.swig.p1_affine;

public class BlstPublicKey implements PublicKey {
  private static final int COMPRESSED_PK_SIZE = 48;
  private static final int UNCOMPRESSED_PK_LENGTH = 49;

  public static BlstPublicKey fromBytes(Bytes48 compressed) {
    p1_affine ecPoint = new p1_affine();
    if (blst.p1_uncompress(ecPoint, compressed.toArrayUnsafe()) == BLST_ERROR.BLST_SUCCESS) {
      return new BlstPublicKey(ecPoint);
    } else {
      ecPoint.delete();
      throw new IllegalArgumentException("Invalid PublicKey bytes: " + compressed);
    }
  }

  public static BlstPublicKey aggregate(List<BlstPublicKey> publicKeys) {
    checkArgument(publicKeys.size() > 0);

    p1 sum = new p1();
    blst.p1_from_affine(sum, publicKeys.get(0).ecPoint);
    for (int i = 1; i < publicKeys.size(); i++) {
      blst.p1_add_affine(sum, sum, publicKeys.get(i).ecPoint);
    }
    p1_affine res = new p1_affine();
    blst.p1_to_affine(res, sum);
    sum.delete();

    return new BlstPublicKey(res);
  }

  final p1_affine ecPoint;

  public BlstPublicKey(p1_affine ecPoint) {
    this.ecPoint = ecPoint;
  }

  @Override
  public void forceValidation() throws IllegalArgumentException {
    if (blst.p1_affine_on_curve(ecPoint) == 0) {
      throw new IllegalArgumentException("Invalid PublicKey: " + this);
    }
  }

  @Override
  public Bytes48 toBytesCompressed() {
    byte[] res = new byte[COMPRESSED_PK_SIZE];
    blst.p1_affine_compress(res, ecPoint);
    return Bytes48.wrap(res);
  }

  @Override
  public int hashCode() {
    return toBytesCompressed().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BlstPublicKey that = (BlstPublicKey) o;
    return Objects.equals(toBytesCompressed(), that.toBytesCompressed());
  }
}
