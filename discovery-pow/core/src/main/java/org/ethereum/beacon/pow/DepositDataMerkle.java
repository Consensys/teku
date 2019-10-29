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

package org.ethereum.beacon.pow;

import static tech.pegasys.artemis.util.bytes.BytesValue.concat;

import java.util.function.Function;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

/** Abstract implementation of {@link MerkleTree} for {@link DepositData} */
abstract class DepositDataMerkle implements MerkleTree<DepositData> {

  private final Hash32[] zeroHashes;
  private final Function<BytesValue, Hash32> hashFunction;

  DepositDataMerkle(Function<BytesValue, Hash32> hashFunction, int treeDepth) {
    this.hashFunction = hashFunction;
    this.zeroHashes = new Hash32[treeDepth];
  }

  //     zero_bytes_32: bytes32
  //    pubkey_root: bytes32 = sha256(concat(pubkey, slice(zero_bytes_32, start=0, len=16)))
  //    signature_root: bytes32 = sha256(concat(
  //        sha256(slice(signature, start=0, len=64)),
  //        sha256(concat(slice(signature, start=64, len=32), zero_bytes_32))
  //    ))
  //    value: bytes32 = sha256(concat(
  //        sha256(concat(pubkey_root, withdrawal_credentials)),
  //        sha256(concat(
  //            amount,
  //            slice(zero_bytes_32, start=0, len=24),
  //            signature_root,
  //        ))
  //    ))
  static Hash32 createDepositDataValue(
      DepositData depositData, Function<BytesValue, Hash32> hashFunction) {
    BytesValue zero_bytes_32 = Bytes32.ZERO.slice(0);
    Hash32 pubkey_root =
        hashFunction.apply(depositData.getPubKey().concat(zero_bytes_32.slice(0, 16)));
    Hash32 signature_root =
        hashFunction.apply(
            hashFunction
                .apply(depositData.getSignature().slice(0, 64))
                .concat(
                    hashFunction.apply(
                        depositData.getSignature().slice(64, 32).concat(zero_bytes_32))));
    Hash32 value =
        hashFunction.apply(
            hashFunction
                .apply(pubkey_root.concat(depositData.getWithdrawalCredentials()))
                .concat(
                    hashFunction.apply(
                        depositData
                            .getAmount()
                            .toBytes8LittleEndian()
                            .concat(zero_bytes_32.slice(0, 24).concat(signature_root)))));

    return value;
  }

  Function<BytesValue, Hash32> getHashFunction() {
    return hashFunction;
  }

  Hash32 getZeroHash(int distanceFromBottom) {
    if (zeroHashes[distanceFromBottom] == null) {
      if (distanceFromBottom == 0) {
        zeroHashes[0] = Hash32.ZERO;
      } else {
        Hash32 lowerZeroHash = getZeroHash(distanceFromBottom - 1);
        zeroHashes[distanceFromBottom] = hashFunction.apply(concat(lowerZeroHash, lowerZeroHash));
      }
    }
    return zeroHashes[distanceFromBottom];
  }

  void verifyIndexNotTooBig(int index) {
    if (index > getLastIndex()) {
      throw new RuntimeException(
          String.format("Max element index is %s, asked for %s!", getLastIndex(), index));
    }
  }

  Hash32 mixinLength(BytesValue node, int length) {
    return getHashFunction().apply(BytesValue.concat(node, encodeLength(length)));
  }

  Bytes32 encodeLength(int length) {
    return Bytes32.rightPad(UInt64.valueOf(length).toBytes8LittleEndian());
  }
}
