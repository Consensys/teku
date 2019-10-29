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

package org.ethereum.beacon.consensus.hasher;

import java.util.List;
import java.util.function.Function;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.spec.SpecConstantsResolver;
import org.ethereum.beacon.core.types.Hashable;
import org.ethereum.beacon.ssz.SSZBuilder;
import org.ethereum.beacon.ssz.SSZHasher;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * An object hasher implementation using Tree Hash algorithm described in the spec.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/dev/specs/simple-serialize.md#tree-hash">Tree
 *     Hash</a> in the spec.
 */
public class SSZObjectHasher implements ObjectHasher<Hash32> {

  private final SSZHasher sszHasher;

  public SSZObjectHasher(SSZHasher sszHasher) {
    this.sszHasher = sszHasher;
  }

  public static SSZObjectHasher createIncremental(
      SpecConstants constants, Function<BytesValue, Hash32> hashFunction) {
    return create(constants, hashFunction, true);
  }

  public static SSZObjectHasher create(
      SpecConstants constants, Function<BytesValue, Hash32> hashFunction, boolean incremental) {
    SSZHasher sszHasher =
        new SSZBuilder()
            .withExternalVarResolver(new SpecConstantsResolver(constants))
            .withExtraObjectCreator(SpecConstants.class, constants)
            .withIncrementalHasher(incremental)
            .buildHasher(hashFunction);
    return new SSZObjectHasher(sszHasher);
  }

  @Override
  public Hash32 getHash(Object input) {
    Function<Object, Hash32> hasher = o -> Hash32.wrap(Bytes32.wrap(sszHasher.hash(o)));
    if (input instanceof Hashable) {
      return ((Hashable<Hash32>) input).getHash(hasher);
    } else {
      return hasher.apply(input);
    }
  }

  @Override
  public Hash32 getHashTruncateLast(Object input) {
    if (input instanceof List) {
      throw new RuntimeException("Lists are not supported in truncated hash");
    } else {
      return Hash32.wrap(Bytes32.wrap(sszHasher.hashTruncateLast(input, input.getClass())));
    }
  }
}
