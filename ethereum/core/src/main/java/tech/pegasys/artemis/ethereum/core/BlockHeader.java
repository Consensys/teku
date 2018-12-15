/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.ethereum.core;


import tech.pegasys.artemis.ethereum.rlp.RLPInput;
import tech.pegasys.artemis.ethereum.rlp.RLPOutput;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt256;

import java.util.Objects;

/**
 * A mined Ethereum block header.
 */
public class BlockHeader extends SealableBlockHeader {

  public static final int MAX_EXTRA_DATA_BYTES = 32;

  public static final long GENESIS_BLOCK_NUMBER = 0L;

  private final Hash mixHash;

  private final long nonce;
  private final BlockHashFunction hashFunction;

  private Hash hash;

  public BlockHeader(final Hash parentHash, final Hash ommersHash, final Address coinbase,
      final Hash stateRoot, final Hash transactionsRoot, final Hash receiptsRoot,
      final LogsBloomFilter logsBloom, final UInt256 difficulty, final long number,
      final long gasLimit, final long gasUsed, final long timestamp, final BytesValue extraData,
      final Hash mixHash, final long nonce, final BlockHashFunction hashFunction) {
    super(parentHash, ommersHash, coinbase, stateRoot, transactionsRoot, receiptsRoot, logsBloom,
        difficulty, number, gasLimit, gasUsed, timestamp, extraData);
    this.mixHash = mixHash;
    this.nonce = nonce;
    this.hashFunction = hashFunction;
  }

  /**
   * Returns the block mixed hash.
   *
   * @return the block mixed hash
   */
  public Hash mixHash() {
    return mixHash;
  }

  /**
   * Returns the block nonce.
   *
   * @return the block nonce
   */
  public long nonce() {
    return nonce;
  }

  /**
   * Returns the block header hash.
   *
   * @return the block header hash
   */
  public Hash hash() {
    if (hash == null) {
      hash = hashFunction.apply(this);
    }
    return hash;
  }

  /**
   * Write an RLP representation.
   *
   * @param out The RLP output to write to
   */
  public void writeTo(RLPOutput out) {
    out.startList();

    out.writeBytesValue(parentHash);
    out.writeBytesValue(ommersHash);
    out.writeBytesValue(coinbase);
    out.writeBytesValue(stateRoot);
    out.writeBytesValue(transactionsRoot);
    out.writeBytesValue(receiptsRoot);
    out.writeBytesValue(logsBloom.bytes());
    out.writeUInt256Scalar(difficulty);
    out.writeLongScalar(number);
    out.writeLongScalar(gasLimit);
    out.writeLongScalar(gasUsed);
    out.writeLongScalar(timestamp);
    out.writeBytesValue(extraData);
    out.writeBytesValue(mixHash);
    out.writeLong(nonce);

    out.endList();
  }

  public static BlockHeader readFrom(RLPInput input, BlockHashFunction hashFunction) {
    input.enterList();
    BlockHeader header = new BlockHeader(Hash.wrap(input.readBytes32()),
        Hash.wrap(input.readBytes32()), Address.readFrom(input), Hash.wrap(input.readBytes32()),
        Hash.wrap(input.readBytes32()), Hash.wrap(input.readBytes32()),
        LogsBloomFilter.readFrom(input), input.readUInt256Scalar(), input.readLongScalar(),
        input.readLongScalar(), input.readLongScalar(), input.readLongScalar(),
        input.readBytesValue(), Hash.wrap(input.readBytes32()), input.readLong(), hashFunction);
    input.leaveList();
    return header;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BlockHeader)) {
      return false;
    }
    BlockHeader other = (BlockHeader) obj;
    return hash().equals(other.hash());
  }

  @Override
  public int hashCode() {
    return Objects.hash(hash());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BlockHeader{");
    sb.append("hash=").append(hash()).append(", ");
    sb.append("parentHash=").append(parentHash).append(", ");
    sb.append("ommersHash=").append(ommersHash).append(", ");
    sb.append("coinbase=").append(coinbase).append(", ");
    sb.append("stateRoot=").append(stateRoot).append(", ");
    sb.append("transactionsRoot=").append(transactionsRoot).append(", ");
    sb.append("receiptsRoot=").append(receiptsRoot).append(", ");
    sb.append("logsBloom=").append(logsBloom).append(", ");
    sb.append("difficulty=").append(difficulty).append(", ");
    sb.append("number=").append(number).append(", ");
    sb.append("gasLimit=").append(gasLimit).append(", ");
    sb.append("gasUsed=").append(gasUsed).append(", ");
    sb.append("timestamp=").append(timestamp).append(", ");
    sb.append("extraData=").append(extraData).append(", ");
    sb.append("mixHash=").append(mixHash).append(", ");
    sb.append("nonce=").append(nonce);
    return sb.append("}").toString();
  }

}
