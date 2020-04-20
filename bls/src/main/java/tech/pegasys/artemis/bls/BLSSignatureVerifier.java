package tech.pegasys.artemis.bls;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;

public interface BLSSignatureVerifier {

  class InvalidSignatureException extends Exception {

    public InvalidSignatureException(String message) {
      super(message);
    }
  }

  BLSSignatureVerifier SIMPLE = BLS::fastAggregateVerify;

  boolean verify(List<BLSPublicKey> publicKey, Bytes message, BLSSignature signature);

  default boolean verify(BLSPublicKey publicKey, Bytes message, BLSSignature signature) {
    return verify(Collections.singletonList(publicKey), message, signature);
  }

  default void verifyAndThrow(
      BLSPublicKey publicKey, Bytes message, BLSSignature signature, Supplier<String> errMessage)
      throws InvalidSignatureException {
    if (!verify(publicKey, message, signature)) {
      throw new InvalidSignatureException(errMessage.get());
    }
  }

  default void verifyAndThrow(
      BLSPublicKey publicKey, Bytes message, BLSSignature signature, String errMessage)
      throws InvalidSignatureException {
    verifyAndThrow(publicKey, message, signature, () -> errMessage);
  }

  default void verifyAndThrow(BLSPublicKey publicKey, Bytes message, BLSSignature signature)
      throws InvalidSignatureException {
    verifyAndThrow(publicKey, message, signature, () -> "Invalid signature");
  }
}
