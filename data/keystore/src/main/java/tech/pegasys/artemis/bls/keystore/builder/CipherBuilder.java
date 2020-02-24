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

package tech.pegasys.artemis.bls.keystore.builder;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.keystore.model.Cipher;
import tech.pegasys.artemis.bls.keystore.model.CipherFunction;
import tech.pegasys.artemis.bls.keystore.model.CipherParam;

public final class CipherBuilder {
  private CipherFunction cipherFunction = CipherFunction.AES_128_CTR;
  private CipherParam cipherParam;
  private Bytes message = Bytes.EMPTY;

  private CipherBuilder() {}

  public static CipherBuilder aCipher() {
    return new CipherBuilder();
  }

  public CipherBuilder withCipherFunction(final CipherFunction cipherFunction) {
    this.cipherFunction = cipherFunction;
    return this;
  }

  public CipherBuilder withCipherParam(final CipherParam cipherParam) {
    this.cipherParam = cipherParam;
    return this;
  }

  public CipherBuilder withMessage(final Bytes message) {
    this.message = message;
    return this;
  }

  public Cipher build() {
    Objects.requireNonNull(cipherParam);
    return new Cipher(cipherFunction, cipherParam, message);
  }
}
