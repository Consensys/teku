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

package tech.pegasys.artemis.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.security.GeneralSecurityException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Cipher {
  private static final BouncyCastleProvider BC = new BouncyCastleProvider();
  private final CipherFunction cipherFunction;
  private final CipherParam cipherParam;
  private final Bytes message;

  @JsonCreator
  public Cipher(
      @JsonProperty(value = "function", required = true) final CipherFunction cipherFunction,
      @JsonProperty(value = "params", required = true) final CipherParam cipherParam,
      @JsonProperty(value = "message", required = true) final Bytes message) {
    this.cipherFunction = cipherFunction;
    this.cipherParam = cipherParam;
    this.message = message;
  }

  @JsonProperty(value = "function")
  public CipherFunction getCipherFunction() {
    return cipherFunction;
  }

  @JsonProperty(value = "params")
  public CipherParam getCipherParam() {
    return cipherParam;
  }

  @JsonProperty(value = "message")
  public Bytes getMessage() {
    return message;
  }

  public Bytes decrypt(final Bytes decryptionKey) {
    if (decryptionKey.size() < 16) {
      throw new RuntimeException("Invalid Decryption key size");
    }

    final SecretKeySpec secretKey =
        new SecretKeySpec(decryptionKey.slice(0, 16).toArrayUnsafe(), "AES");

    final IvParameterSpec ivParameterSpec =
        new IvParameterSpec(getCipherParam().getIv().toArrayUnsafe());
    try {
      final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding", BC);
      cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
      final Bytes updatedBytes = Bytes.wrap(cipher.update(getMessage().toArrayUnsafe()));
      return Bytes.wrap(updatedBytes, Bytes.wrap(cipher.doFinal()));
    } catch (final GeneralSecurityException e) {
      throw new RuntimeException("Error applying aes-128-ctr cipher function", e);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("function", cipherFunction)
        .add("params", cipherParam)
        .add("message", message)
        .toString();
  }
}
