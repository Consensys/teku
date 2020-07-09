package tech.pegasys.teku.bls.supra;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.scalar;

public class SecretKey {

  public static SecretKey fromBytes(Bytes bytes) {
    checkArgument(bytes.size() == 32, "Expected 32 bytes but received %s.", bytes.size());
    scalar scalarVal = new scalar();
    blst.scalar_from_bendian(scalarVal, bytes.toArrayUnsafe());
    return new SecretKey(scalarVal);
  }

  public final scalar scalarVal;

  public SecretKey(scalar scalarVal) {
    this.scalarVal = scalarVal;
  }

  public Bytes toBytes() {
    byte[] res = new byte[32];
    blst.bendian_from_scalar(res, scalarVal);
    return Bytes.wrap(res);
  }
}
