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

package tech.pegasys.artemis.util.bls.keystore;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.crypto.params.KeyParameter;
import tech.pegasys.artemis.util.message.BouncyCastleMessageDigestFactory;

/**
 * BLS Key Store implementation EIP-2335
 *
 * @see <a href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2335.md">EIP-2335</a>
 */
public class KeyStore {
  private final KeyStoreData keyStoreData;

  public KeyStore(final KeyStoreData keyStoreData) {
    this.keyStoreData = keyStoreData;
  }

  public KeyStoreData getKeyStoreData() {
    return keyStoreData;
  }

  public boolean validatePassword(final String password) {
    final Bytes derivedKey = keyDerivationFunction(password.getBytes(UTF_8));
    final Bytes dkSlice = derivedKey.slice(16, 16);
    final Bytes preImage = Bytes.wrap(dkSlice, keyStoreData.getCrypto().getCipher().getMessage());
    final MessageDigest messageDigest = sha256Digest();
    preImage.update(messageDigest);
    final Bytes checksum = Bytes.wrap(messageDigest.digest());

    return Objects.equals(checksum, keyStoreData.getCrypto().getChecksum().getMessage());
  }

  private MessageDigest sha256Digest() {
    final MessageDigest messageDigest;
    try {
      messageDigest = BouncyCastleMessageDigestFactory.create("sha256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Unable to create message digest", e);
    }
    return messageDigest;
  }

  private Bytes keyDerivationFunction(final byte[] password) {
    final Kdf kdf = keyStoreData.getCrypto().getKdf();

    if (kdf.getParam() instanceof SCryptParam) {
      SCryptParam sCryptParam = (SCryptParam) kdf.getParam();
      return scrypt(password, sCryptParam);
    } else if (kdf.getParam() instanceof Pbkdf2Param) {
      final Pbkdf2Param pbkdf2Param = (Pbkdf2Param) kdf.getParam();
      return pbkdf2(password, pbkdf2Param);
    }

    throw new RuntimeException("Unsupported crypto function");
  }

  private Bytes scrypt(final byte[] password, final SCryptParam sCryptParam) {
    return Bytes.wrap(
        SCrypt.generate(
            password,
            sCryptParam.getSalt().toArrayUnsafe(),
            sCryptParam.getN(),
            sCryptParam.getR(),
            sCryptParam.getP(),
            sCryptParam.getDerivedKeyLength()));
  }

  private Bytes pbkdf2(final byte[] password, final Pbkdf2Param pbkdf2Param) {
    final String pseudoRandomFunction = pbkdf2Param.getPrf().toLowerCase();
    final Digest digest;
    switch (pseudoRandomFunction) {
      case "hmac-sha1":
        digest = new SHA1Digest();
        break;
      case "hmac-sha256":
        digest = new SHA256Digest();
        break;
      case "hmac-sha512":
        digest = new SHA512Digest();
        break;
      default:
        throw new RuntimeException("Unsupported pseudo random function for PBKDF2");
    }

    PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(digest);
    gen.init(password, pbkdf2Param.getSalt().toArrayUnsafe(), pbkdf2Param.getIterativeCount());
    final byte[] key =
        ((KeyParameter) gen.generateDerivedParameters(pbkdf2Param.getDerivedKeyLength() * 8))
            .getKey();
    return Bytes.wrap(key);
  }
}
