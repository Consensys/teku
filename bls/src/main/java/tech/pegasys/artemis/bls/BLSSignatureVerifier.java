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
