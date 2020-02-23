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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.tuweni.crypto.Hash.sha2_256;

import com.google.common.annotations.VisibleForTesting;
import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import tech.pegasys.artemis.bls.keystore.builder.ChecksumBuilder;
import tech.pegasys.artemis.bls.keystore.builder.CipherBuilder;
import tech.pegasys.artemis.bls.keystore.builder.CipherParamBuilder;
import tech.pegasys.artemis.bls.keystore.builder.KdfBuilder;
import tech.pegasys.artemis.bls.keystore.builder.KeyStoreDataBuilder;
import tech.pegasys.artemis.bls.keystore.builder.Pbkdf2ParamBuilder;
import tech.pegasys.artemis.bls.keystore.builder.SCryptParamBuilder;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.SecretKey;

/**
 * BLS Key Store implementation EIP-2335
 *
 * @see <a href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2335.md">EIP-2335</a>
 */
public class KeyStore {
  private static final BouncyCastleProvider BC = new BouncyCastleProvider();
  private final KeyStoreData keyStoreData;

  public KeyStore(final KeyStoreData keyStoreData) {
    this.keyStoreData = keyStoreData;
  }

  public KeyStoreData getKeyStoreData() {
    return keyStoreData;
  }

  /**
   * Encrypt the given BLS12-381 secret key with the password format using random salt and AES-IV
   * and default parameter values of specified crypto function.
   *
   * @param secret BLS12-381 secret key in Bytes
   * @param password The password to be used for encryption
   * @param path Path as defined in EIP-2334. Can be empty String.
   * @param cryptoFunction The KDF Crypto function to use with the default parameters and random
   *     salt.
   * @return The constructed KeyStore with encrypted secret and other details as defined by the
   *     EIP-2335 standard.
   */
  public static KeyStore encrypt(
      final Bytes secret,
      final String password,
      final String path,
      final CryptoFunction cryptoFunction) {
    final KdfParam kdfParam =
        cryptoFunction == CryptoFunction.SCRYPT
            ? SCryptParamBuilder.aSCryptParam().build()
            : Pbkdf2ParamBuilder.aPbkdf2Param().build();
    final CipherParam cipherParam = CipherParamBuilder.aCipherParam().build();
    return encrypt(secret, password, path, kdfParam, cipherParam);
  }

  @VisibleForTesting
  public static KeyStore encrypt(
      final Bytes secret,
      final String password,
      final String path,
      final KdfParam kdfParam,
      final CipherParam cipherParam) {

    final Bytes decryptionKey = kdfParam.decryptionKey(password.getBytes(UTF_8));
    final SecretKeySpec secretKey =
        new SecretKeySpec(decryptionKey.slice(0, 16).toArrayUnsafe(), "AES");

    final IvParameterSpec ivParameterSpec =
        new IvParameterSpec(cipherParam.getIv().toArrayUnsafe());

    final Bytes cipherMessage;
    final Bytes checksumMessage;
    try {
      final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding", BC);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
      cipherMessage =
          Bytes.wrap(
              Bytes.wrap(cipher.update(secret.toArrayUnsafe())), Bytes.wrap(cipher.doFinal()));
      checksumMessage = sha2_256(Bytes.wrap(decryptionKey.slice(16, 16), cipherMessage));
    } catch (final GeneralSecurityException e) {
      throw new RuntimeException("Error applying aes-128-ctr cipher function", e);
    }

    final Checksum checksum = ChecksumBuilder.aChecksum().withMessage(checksumMessage).build();
    final tech.pegasys.artemis.bls.keystore.Cipher cipher =
        CipherBuilder.aCipher().withCipherParam(cipherParam).withMessage(cipherMessage).build();
    final Kdf kdf = KdfBuilder.aKdf().withParam(kdfParam).build();
    final Crypto crypto = new Crypto(kdf, checksum, cipher);

    final Bytes pubKey = new PublicKey(SecretKey.fromBytes(Bytes48.leftPad(secret))).toBytesCompressed();
    final KeyStoreData keyStoreData =
        KeyStoreDataBuilder.aKeyStoreData()
            .withCrypto(crypto)
            .withPath(path)
            .withPubkey(pubKey)
            .build();

    return new KeyStore(keyStoreData);
  }

  public boolean validatePassword(final String password) {
    final Bytes decryptionKey =
        keyStoreData.getCrypto().getKdf().getParam().decryptionKey(password.getBytes(UTF_8));
    final Bytes dkSlice = decryptionKey.slice(16, 16);
    final Bytes preImage = Bytes.wrap(dkSlice, keyStoreData.getCrypto().getCipher().getMessage());
    final Bytes checksum = sha2_256(preImage);

    return Objects.equals(checksum, keyStoreData.getCrypto().getChecksum().getMessage());
  }

  public Bytes decrypt(final String password) {
    if (!validatePassword(password)) {
      throw new RuntimeException("Invalid password");
    }

    final Bytes decryptionKey =
        keyStoreData.getCrypto().getKdf().getParam().decryptionKey(password.getBytes(UTF_8));

    return decryptSecret(decryptionKey, keyStoreData.getCrypto().getCipher());
  }

  private Bytes decryptSecret(
      final Bytes decryptionKey, final tech.pegasys.artemis.bls.keystore.Cipher cipher) {
    if (decryptionKey.size() < 16) {
      throw new RuntimeException("Invalid Decryption key size");
    }

    final SecretKeySpec secretKey =
        new SecretKeySpec(decryptionKey.slice(0, 16).toArrayUnsafe(), "AES");

    final IvParameterSpec ivParameterSpec =
        new IvParameterSpec(cipher.getCipherParam().getIv().toArrayUnsafe());
    try {
      final javax.crypto.Cipher aesCtrCipher =
          javax.crypto.Cipher.getInstance("AES/CTR/NoPadding", BC);
      aesCtrCipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
      final Bytes updatedBytes =
          Bytes.wrap(aesCtrCipher.update(cipher.getMessage().toArrayUnsafe()));
      return Bytes.wrap(updatedBytes, Bytes.wrap(aesCtrCipher.doFinal()));
    } catch (final GeneralSecurityException e) {
      throw new RuntimeException("Error applying aes-128-ctr cipher function", e);
    }
  }
}
