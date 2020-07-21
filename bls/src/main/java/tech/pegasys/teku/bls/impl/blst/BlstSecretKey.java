package tech.pegasys.teku.bls.impl.blst;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.impl.SecretKey;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p1;
import tech.pegasys.teku.bls.impl.blst.swig.p1_affine;
import tech.pegasys.teku.bls.impl.blst.swig.scalar;

public class BlstSecretKey implements SecretKey {

  public static BlstSecretKey fromBytes(Bytes bytes) {
    checkArgument(bytes.size() == 48, "Expected 48 bytes but received %s.", bytes.size());
    scalar scalarVal = new scalar();
    blst.scalar_from_bendian(scalarVal, bytes.slice(16).toArrayUnsafe());
    return new BlstSecretKey(scalarVal);
  }

  public static BlstSecretKey generateNew(Random random) {
    byte[] ikm = new byte[128];
    random.nextBytes(ikm);
    scalar scalar = new scalar();
    blst.keygen(scalar, ikm, null);
    return new BlstSecretKey(scalar);
  }

  public final scalar scalarVal;

  public BlstSecretKey(scalar scalarVal) {
    this.scalarVal = scalarVal;
  }

  @Override
  public Bytes toBytes() {
    byte[] res = new byte[32];
    blst.bendian_from_scalar(res, scalarVal);
    return Bytes48.leftPad(Bytes.wrap(res));
  }

  @Override
  public Signature sign(Bytes message) {
    return BlstBLS12381.sign(this, message);
  }

  @Override
  public void destroy() {
    // TODO
  }

  @Override
  public BlstPublicKey derivePublicKey() {
    p1 p1 = new p1();
    blst.sk_to_pk_in_g1(p1, scalarVal);
    p1_affine p1_affine = new p1_affine();
    blst.p1_to_affine(p1_affine, p1);
    p1.delete();
    return new BlstPublicKey(p1_affine);
  }

  @Override
  public int hashCode() {
    return toBytes().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BlstSecretKey that = (BlstSecretKey) o;
    return Objects.equals(toBytes(), that.toBytes());
  }
}
