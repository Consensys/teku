/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.crypto;

import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes8;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * An interface of message parameters that are used by BLS381 signature scheme.
 *
 * <p>According to the spec, message parameters are its hash and domain.
 *
 * @see MessageParametersMapper
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md">https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md</a>
 */
public interface MessageParameters {

  static MessageParameters create(Hash32 hash, Bytes8 domain) {
    return new Impl(hash, domain);
  }

  static MessageParameters create(Hash32 hash, UInt64 domain) {
    return new Impl(hash, domain.toBytes8LittleEndian());
  }

  /**
   * Returns a hash of the message.
   *
   * @return hash value.
   */
  Hash32 getHash();

  /**
   * Returns message domain.
   *
   * @return domain value.
   */
  Bytes8 getDomain();

  /** A straightforward implementation of {@link MessageParameters}. */
  class Impl implements MessageParameters {
    private final Hash32 hash;
    private final Bytes8 domain;

    public Impl(Hash32 hash, Bytes8 domain) {
      this.hash = hash;
      this.domain = domain;
    }

    @Override
    public Hash32 getHash() {
      return hash;
    }

    @Override
    public Bytes8 getDomain() {
      return domain;
    }
  }
}
