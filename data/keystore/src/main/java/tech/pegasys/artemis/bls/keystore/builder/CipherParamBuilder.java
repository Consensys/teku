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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.keystore.model.CipherParam;

public final class CipherParamBuilder {
  private Bytes iv;

  private CipherParamBuilder() {}

  public static CipherParamBuilder aCipherParam() {
    return new CipherParamBuilder();
  }

  public CipherParamBuilder withIv(final Bytes iv) {
    this.iv = iv;
    return this;
  }

  public CipherParam build() {
    if (iv == null) {
      iv = Bytes.random(16);
    }
    return new CipherParam(iv);
  }
}
