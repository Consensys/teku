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

package org.ethereum.beacon.ssz.visitor;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.ethereum.beacon.ssz.type.SSZType.Type.BASIC;
import static org.ethereum.beacon.ssz.type.SSZType.Type.LIST;
import static org.ethereum.beacon.ssz.type.SSZType.Type.VECTOR;
import static tech.pegasys.artemis.util.bytes.BytesValue.concat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.consensys.cava.ssz.SSZException;
import org.ethereum.beacon.ssz.access.SSZListAccessor;
import org.ethereum.beacon.ssz.access.SSZUnionAccessor.UnionInstanceAccessor;
import org.ethereum.beacon.ssz.type.SSZBasicType;
import org.ethereum.beacon.ssz.type.SSZCompositeType;
import org.ethereum.beacon.ssz.type.SSZContainerType;
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.SSZUnionType;
import org.ethereum.beacon.ssz.type.list.SSZBitListType;
import org.ethereum.beacon.ssz.type.list.SSZListType;
import org.ethereum.beacon.ssz.visitor.SosSerializer.SerializerResult;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.BytesValues;
import tech.pegasys.artemis.util.bytes.MutableBytesValue;
import tech.pegasys.artemis.util.collections.Bitlist;

public class SSZSimpleHasher implements SSZVisitor<MerkleTrie, Object> {

  private final Hash32[] zeroHashes = new Hash32[64];
  final SSZVisitorHandler<SerializerResult> serializer;
  final Function<BytesValue, Hash32> hashFunction;
  final int bytesPerChunk;

  public SSZSimpleHasher(
      SSZVisitorHandler<SerializerResult> serializer,
      Function<BytesValue, Hash32> hashFunction,
      int bytesPerChunk) {
    this.serializer = serializer;
    this.hashFunction = hashFunction;
    this.bytesPerChunk = bytesPerChunk;
  }

  @Override
  public MerkleTrie visitBasicValue(SSZBasicType descriptor, Object value) {
    SerializerResult sszSerializerResult = serializer.visitAny(descriptor, value);
    return merkleize(pack(sszSerializerResult.getSerializedBody()), null);
  }

  @Override
  public MerkleTrie visitUnion(
      SSZUnionType type, Object param, ChildVisitor<Object, MerkleTrie> childVisitor) {
    UnionInstanceAccessor unionInstanceAccessor =
        type.getAccessor().getInstanceAccessor(type.getTypeDescriptor());
    int typeIndex = unionInstanceAccessor.getTypeIndex(param);
    List<BytesValue> chunks;
    if (type.isNullable() && typeIndex == 0) {
      chunks = emptyList();
    } else {
      Object value = unionInstanceAccessor.getChildValue(param, typeIndex);
      chunks = singletonList(childVisitor.apply(typeIndex, value).getFinalRoot());
    }
    MerkleTrie merkle = merkleize(chunks, null);
    Hash32 mixInType = hashFunction.apply(concat(merkle.getPureRoot(), serializeLength(typeIndex)));
    merkle.setFinalRoot(mixInType);
    return merkle;
  }

  @Override
  public MerkleTrie visitComposite(
      SSZCompositeType type, Object rawValue, ChildVisitor<Object, MerkleTrie> childVisitor) {
    MerkleTrie merkle;
    List<BytesValue> chunks = new ArrayList<>();
    if (type.getChildrenCount(rawValue) == 0) {
      // empty chunk list
    } else if ((type.getType() == LIST || type.getType() == VECTOR)
        && ((SSZListType) type).getElementType().getType() == BASIC) {
      SerializerResult sszSerializerResult = serializer.visitAny(type, rawValue);
      BytesValue serialization;
      // Strip size bit in Bitlist
      if (type.getType() == LIST && ((SSZListType) type).isBitType()) {
        serialization = removeBitListSize(rawValue, sszSerializerResult.getSerializedBody());
      } else {
        serialization = sszSerializerResult.getSerializedBody();
      }
      chunks = pack(serialization);
    } else {
      for (int i = 0; i < type.getChildrenCount(rawValue); i++) {
        chunks.add(childVisitor.apply(i, type.getChild(rawValue, i)).getFinalRoot());
      }
    }
    Long padFor = null;
    if (type.getType() == LIST) {
      padFor = chunkCount(type);
    }
    merkle = merkleize(chunks, padFor);
    if (type.getType() == LIST) {
      SSZListAccessor listAccessor =
          (SSZListAccessor) type.getAccessor().getInstanceAccessor(type.getTypeDescriptor());
      int elementCount;
      if (((SSZListType) type).isBitType()) {
        elementCount = ((Bitlist) rawValue).size();
      } else {
        elementCount = listAccessor.getChildrenCount(rawValue);
      }
      Hash32 mixInLength =
          hashFunction.apply(concat(merkle.getPureRoot(), serializeLength(elementCount)));
      merkle.setFinalRoot(mixInLength);
    }
    return merkle;
  }

  private BytesValue removeBitListSize(Object value, BytesValue bitlist) {
    MutableBytesValue encoded = bitlist.mutableCopy();
    Bitlist obj = (Bitlist) value;
    encoded.setBit(obj.size(), false);
    return encoded.copy();
  }

  protected List<BytesValue> pack(BytesValue value) {
    List<BytesValue> ret = new ArrayList<>();
    int i = 0;
    while (i + bytesPerChunk <= value.size()) {
      ret.add(value.slice(i, bytesPerChunk));
      i += bytesPerChunk;
    }
    if (value.size() % bytesPerChunk != 0) {
      BytesValue last = value.slice(i, value.size() - i);
      BytesValue lastPadded =
          concat(last, BytesValue.wrap(new byte[bytesPerChunk - value.size() % bytesPerChunk]));
      ret.add(lastPadded);
    }
    return ret;
  }

  /**
   * Merkleize chunks using merkle trie
   *
   * @param chunks chunks of standard size
   * @param padFor if provided, chunks are padded with zero chunks to next power of 2 (padFor)
   * @return result trie
   */
  public MerkleTrie merkleize(List<? extends BytesValue> chunks, @Nullable Long padFor) {
    int chunksCount = nextPowerOf2(chunks.size());
    if (padFor != null && padFor > chunksCount) {
      return merkleize(chunks, chunksCount, padFor);
    } else {
      return merkleize(chunks, chunksCount);
    }
  }

  /**
   * Extension of {@link #merkleize(List, int)}, designed to virtually deal with large number of
   * zero leaves added to chunksLeaves up to padFor number
   *
   * @return virtual trie without actual nodes, only with calculated root
   */
  VirtualMerkleTrie merkleize(List<? extends BytesValue> chunks, int chunksLeaves, long padFor) {
    int baseLevel = nextBinaryLog(chunks.size());
    int virtualLevel = nextBinaryLog(padFor);
    MerkleTrie original = merkleize(chunks, chunksLeaves);
    BytesValue root = original.getPureRoot();
    for (int i = baseLevel; i < virtualLevel; ++i) {
      root = hashFunction.apply(concat(root, getZeroHash(i)));
    }

    return new VirtualMerkleTrie(original.nodes, root);
  }

  /**
   * Merkleize chunks using binary tree, using zero hashes on leaves non-occupied by chunks elements
   */
  MerkleTrie merkleize(List<? extends BytesValue> chunks, int chunksLeaves) {
    BytesValue[] nodes = new BytesValue[chunksLeaves * 2];
    for (int i = 0; i < chunksLeaves; i++) {
      nodes[i + chunksLeaves] = i < chunks.size() ? chunks.get(i) : Bytes32.ZERO;
    }

    int len = (chunks.size() - 1) / 2 + 1;
    int pos = chunksLeaves / 2;
    int level = 1;
    while (pos > 0) {
      for (int i = 0; i < len; i++) {
        nodes[pos + i] = hashFunction.apply(concat(nodes[(pos + i) * 2], nodes[(pos + i) * 2 + 1]));
      }
      for (int i = len; i < pos; i++) {
        nodes[pos + i] = getZeroHash(level);
      }
      len = (len - 1) / 2 + 1;
      pos /= 2;
      level++;
    }

    nodes[0] = nodes[1];
    return new MerkleTrie(nodes);
  }

  private long itemLength(SSZType type) {
    if (type instanceof SSZBasicType) {
      return type.getSize();
    } else {
      return 32;
    }
  }

  long chunkCount(SSZType type) {
    if (type instanceof SSZBasicType) {
      return 1;
    } else if (type instanceof SSZBitListType) {
      SSZBitListType bitListType = (SSZBitListType) type;
      long bitSize = type.isFixedSize() ? bitListType.getBitSize() : bitListType.getMaxBitSize();
      return (bitSize + 255) / 256;
    } else if (type instanceof SSZListType) {
      SSZListType listType = (SSZListType) type;
      long size = type.isFixedSize() ? listType.getSize() : listType.getMaxSize();
      if (size <= 0) {
        return 0;
      }
      return (size * itemLength(listType.getElementType()) + 31) / 32;
    } else if (type instanceof SSZContainerType) {
      SSZContainerType containerType = (SSZContainerType) type;
      return containerType.getChildTypes().size();
    } else {
      throw new SSZException(
          String.format("Hasher doesn't know how to calculate chunk count for type %s", type));
    }
  }

  protected int nextPowerOf2(int x) {
    if (x <= 1) {
      return 1;
    } else {
      return Integer.highestOneBit(x - 1) << 1;
    }
  }

  /** Returns exponent of 2 to get x or a bit more (next power of 2) 7 -> 3, 8 -> 3, 9 -> 4 */
  int nextBinaryLog(long x) {
    if (x <= 1) {
      return 0;
    } else {
      return Long.BYTES * Byte.SIZE - Long.numberOfLeadingZeros(x - 1);
    }
  }

  public Hash32 getZeroHash(int distanceFromBottom) {
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

  static BytesValue serializeLength(long len) {
    return concat(
        BytesValues.ofUnsignedIntLittleEndian(len),
        BytesValue.wrap(new byte[Hash32.SIZE - Integer.BYTES]));
  }
}
