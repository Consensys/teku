/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.bls;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;

/**
 * Simple interface to enable pluggable variants of BLS verifier. In a {@link #SIMPLE} case it's
 * just static {@link BLS} methods
 */
public interface BLSSignatureVerifier {

  class InvalidSignatureException extends Exception {

    public InvalidSignatureException(String message) {
      super(message);
    }
  }

  /** Just delegates verify to {@link BLS#fastAggregateVerify(List, Bytes, BLSSignature)} */
  BLSSignatureVerifier SIMPLE = BLS::fastAggregateVerify;

  /**
   * Verifies an aggregate BLS signature against a message using the list of public keys. In case of
   * non-aggregate signature [publicKeys] list should contain just a single entry
   *
   * @param publicKeys The list of public keys, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   * @see BLS#fastAggregateVerify(List, Bytes, BLSSignature)
   */
  boolean verify(List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature);

  /** Shortcut to {@link #verify(List, Bytes, BLSSignature)} for non-aggregate case */
  default boolean verify(BLSPublicKey publicKey, Bytes message, BLSSignature signature) {
    return verify(Collections.singletonList(publicKey), message, signature);
  }

  /**
   * Convenient shortcut to throw exception when signature verification fails
   *
   * @throws InvalidSignatureException when signature is invalid
   */
  default void verifyAndThrow(
      BLSPublicKey publicKey, Bytes message, BLSSignature signature, Supplier<String> errMessage)
      throws InvalidSignatureException {
    if (!verify(publicKey, message, signature)) {
      throw new InvalidSignatureException(errMessage.get());
    }
  }

  /**
   * Convenient shortcut to throw exception when signature verification fails
   *
   * @throws InvalidSignatureException when signature is invalid
   */
  default void verifyAndThrow(
      BLSPublicKey publicKey, Bytes message, BLSSignature signature, String errMessage)
      throws InvalidSignatureException {
    verifyAndThrow(publicKey, message, signature, () -> errMessage);
  }

  /**
   * Convenient shortcut to throw exception when signature verification fails
   *
   * @throws InvalidSignatureException when signature is invalid
   */
  default void verifyAndThrow(BLSPublicKey publicKey, Bytes message, BLSSignature signature)
      throws InvalidSignatureException {
    verifyAndThrow(publicKey, message, signature, () -> "Invalid signature");
  }
}
