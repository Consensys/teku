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

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.util.DigestFactory;
import tech.pegasys.artemis.bls.keystore.model.KdfParam;
import tech.pegasys.artemis.bls.keystore.model.Pbkdf2Param;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;

public class DecryptionKeyGenerator {
  public static Bytes generate(final byte[] password, final KdfParam kdfParam) {
    Objects.requireNonNull(password);
    Objects.requireNonNull(kdfParam);

    return kdfParam instanceof SCryptParam
        ? generate(password, (SCryptParam) kdfParam)
        : generate(password, (Pbkdf2Param) kdfParam);
  }

  private static Bytes generate(final byte[] password, final SCryptParam sCryptParam) {
    return Bytes.wrap(
        SCrypt.generate(
            password,
            sCryptParam.getSalt().toArrayUnsafe(),
            sCryptParam.getN(),
            sCryptParam.getR(),
            sCryptParam.getP(),
            sCryptParam.getDerivedKeyLength()));
  }

  private static Bytes generate(final byte[] password, final Pbkdf2Param pbkdf2Param) {
    PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(DigestFactory.createSHA256());
    gen.init(password, pbkdf2Param.getSalt().toArrayUnsafe(), pbkdf2Param.getIterativeCount());
    final int keySizeInBits = pbkdf2Param.getDerivedKeyLength() * 8;
    final byte[] key = ((KeyParameter) gen.generateDerivedParameters(keySizeInBits)).getKey();
    return Bytes.wrap(key);
  }
}
