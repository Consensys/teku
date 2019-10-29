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

import static java.lang.Math.min;
import static java.util.stream.Collectors.toList;
import static org.ethereum.beacon.ssz.type.SSZType.Type.BASIC;
import static org.ethereum.beacon.ssz.type.SSZType.Type.LIST;
import static org.ethereum.beacon.ssz.type.SSZType.Type.VECTOR;
import static tech.pegasys.artemis.util.bytes.BytesValue.concat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.ethereum.beacon.ssz.incremental.ObservableComposite;
import org.ethereum.beacon.ssz.incremental.UpdateListener;
import org.ethereum.beacon.ssz.type.SSZCompositeType;
import org.ethereum.beacon.ssz.type.list.SSZListType;
import org.ethereum.beacon.ssz.visitor.SosSerializer.SerializerResult;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class SSZIncrementalHasher extends SSZSimpleHasher {
  private static final String INCREMENTAL_HASHER_OBSERVER_ID = "Hasher";

  static class SSZIncrementalTracker implements UpdateListener {
    TreeSet<Integer> elementsUpdated = new TreeSet<>();
    MerkleTrie merkleTree;

    public SSZIncrementalTracker(TreeSet<Integer> elementsUpdated, MerkleTrie merkleTree) {
      this.elementsUpdated = elementsUpdated;
      this.merkleTree = merkleTree;
    }

    public SSZIncrementalTracker() {}

    @Override
    public void childUpdated(int childIndex) {
      elementsUpdated.add(childIndex);
    }

    @Override
    public UpdateListener fork() {
      return new SSZIncrementalTracker(
          (TreeSet<Integer>) elementsUpdated.clone(),
          merkleTree == null ? null : merkleTree.copy());
    }
  }

  public SSZIncrementalHasher(
      SSZVisitorHandler<SerializerResult> serializer,
      Function<BytesValue, Hash32> hashFunction,
      int bytesPerChunk) {
    super(serializer, hashFunction, bytesPerChunk);
  }

  @Override
  public MerkleTrie visitComposite(
      SSZCompositeType type, Object rawValue, ChildVisitor<Object, MerkleTrie> childVisitor) {
    if (rawValue instanceof ObservableComposite) {
      SSZIncrementalTracker tracker =
          (SSZIncrementalTracker)
              ((ObservableComposite) rawValue)
                  .getUpdateListener(INCREMENTAL_HASHER_OBSERVER_ID, SSZIncrementalTracker::new);
      if (tracker.merkleTree == null) {
        tracker.merkleTree = super.visitComposite(type, rawValue, childVisitor);
      } else if (!tracker.elementsUpdated.isEmpty()) {
        if ((type.getType() == LIST || type.getType() == VECTOR)
            && ((SSZListType) type).getElementType().getType() == BASIC) {
          tracker.merkleTree =
              updatePackedTrie(
                  (SSZListType) type, rawValue, tracker.merkleTree, tracker.elementsUpdated);
        } else {
          tracker.merkleTree =
              updateNonPackedTrie(
                  type, rawValue, childVisitor, tracker.merkleTree, tracker.elementsUpdated);
        }
      }
      tracker.elementsUpdated.clear();

      return tracker.merkleTree;
    } else {
      return super.visitComposite(type, rawValue, childVisitor);
    }
  }

  private MerkleTrie updateNonPackedTrie(
      SSZCompositeType type,
      Object value,
      BiFunction<Integer, Object, MerkleTrie> childVisitor,
      MerkleTrie merkleTree,
      SortedSet<Integer> elementsUpdated) {

    return updateTrie(
        type,
        value,
        idx -> childVisitor.apply(idx, type.getChild(value, idx)).getFinalRoot(),
        type.getChildrenCount(value),
        merkleTree,
        elementsUpdated);
  }

  private MerkleTrie updatePackedTrie(
      SSZListType type, Object value, MerkleTrie oldTrie, SortedSet<Integer> elementsUpdated) {

    int typeSize = type.getElementType().getSize();
    int valsPerChunk = bytesPerChunk / typeSize;

    return updateTrie(
        type,
        value,
        idx -> serializePackedChunk(type, value, idx),
        (type.getChildrenCount(value) - 1) / valsPerChunk + 1,
        oldTrie,
        elementsUpdated.stream().map(i -> i / valsPerChunk).distinct().collect(toList()));
  }

  private MerkleTrie updateTrie(
      SSZCompositeType type,
      Object value,
      Function<Integer, BytesValue> childChunkSupplier,
      int newChunksCount,
      MerkleTrie oldTrie,
      Collection<Integer> chunksUpdated) {

    MerkleTrie newTrie = copyWithSize(oldTrie, newChunksCount);
    int newTrieWidth = newTrie.nodes.length / 2;

    int pos = newTrieWidth;

    List<Integer> elementsToRecalc = new ArrayList<>();
    for (int i : chunksUpdated) {
      if (i < newTrieWidth) {
        elementsToRecalc.add(i);
        if (i < newChunksCount) {
          newTrie.nodes[pos + i] = childChunkSupplier.apply(i);
        } else {
          newTrie.nodes[pos + i] = getZeroHash(0);
        }
      }
    }

    int idxShift = 0;
    while (pos > 1) {
      pos /= 2;
      idxShift++;
      int lastIdx = Integer.MAX_VALUE;
      for (int i : elementsToRecalc) {
        int idx = pos + (i >> idxShift);
        if (lastIdx != idx) {
          newTrie.nodes[idx] =
              hashFunction.apply(
                  BytesValue.concat(newTrie.nodes[idx * 2], newTrie.nodes[idx * 2 + 1]));
          lastIdx = idx;
        }
      }
    }
    if (type.getType() == LIST) {
      Hash32 pureRoot = newTrie.getPureRoot();
      long padFor = chunkCount(type);
      if (padFor > newTrieWidth) {
        int baseLevel = nextBinaryLog(newTrieWidth);
        int virtualLevel = nextBinaryLog(padFor);
        for (int i = baseLevel; i < virtualLevel; ++i) {
          pureRoot = hashFunction.apply(concat(pureRoot, getZeroHash(i)));
        }
      }
      Hash32 mixInLength =
          hashFunction.apply(
              BytesValue.concat(pureRoot, serializeLength(type.getChildrenCount(value))));
      newTrie.setFinalRoot(mixInLength);
    } else {
      newTrie.setFinalRoot(newTrie.getPureRoot());
    }
    return newTrie;
  }

  private MerkleTrie copyWithSize(MerkleTrie trie, int newChunksCount) {
    int newSize = (int) nextPowerOf2(newChunksCount) * 2;
    if (newSize == trie.nodes.length) {
      return new MerkleTrie(Arrays.copyOf(trie.nodes, newSize));
    } else {
      BytesValue[] oldNodes = trie.nodes;
      BytesValue[] newNodes = new BytesValue[newSize];
      int oldPos = oldNodes.length / 2;
      int newPos = newNodes.length / 2;
      int size = min(newChunksCount, trie.nodes.length / 2);
      int dist = 0;
      while (newPos > 0) {
        System.arraycopy(oldNodes, oldPos, newNodes, newPos, size);
        Arrays.fill(newNodes, newPos + size, newPos * 2, getZeroHash(dist));
        oldPos /= 2;
        newPos /= 2;
        size = size == 1 ? 0 : (size - 1) / 2 + 1;
        dist++;
      }

      return new MerkleTrie(newNodes);
    }
  }

  private BytesValue serializePackedChunk(
      SSZListType basicListType, Object listValue, int chunkIndex) {
    int typeSize = basicListType.getElementType().getSize();
    int valsPerChunk = bytesPerChunk / typeSize;
    if (valsPerChunk * typeSize != bytesPerChunk) {
      throw new UnsupportedOperationException(
          "Type size ("
              + typeSize
              + ") which is not a factor of hasher chunk size ("
              + bytesPerChunk
              + ") is not yet supported.");
    }
    int idx = chunkIndex * valsPerChunk;
    int len = Math.min(valsPerChunk, basicListType.getChildrenCount(listValue) - idx);
    BytesValue chunk = serializer.visitList(basicListType, listValue, idx, len).getSerializedBody();
    if (len < valsPerChunk) {
      chunk = BytesValue.concat(chunk, BytesValue.wrap(new byte[bytesPerChunk - chunk.size()]));
    }
    return chunk;
  }
}
