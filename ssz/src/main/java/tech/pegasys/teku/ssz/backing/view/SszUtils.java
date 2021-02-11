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

package tech.pegasys.teku.ssz.backing.view;

import com.google.common.collect.Streams;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.SszMutableVector;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas.SszBitVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas.SszByteVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.sos.SszReader;

/** Handy view tool methods */
public class SszUtils {

  public static <C, V extends SszData> SszList<V> toSszList(
      SszSchema<? extends SszList<V>> type, Iterable<C> list, Function<C, V> converter) {
    return toSszList(type, Streams.stream(list).map(converter).collect(Collectors.toList()));
  }

  public static <V extends SszData> SszList<V> toSszList(
      SszSchema<? extends SszList<V>> type, Iterable<V> list) {
    SszMutableList<V> ret = type.getDefault().createWritableCopy();
    list.forEach(ret::append);
    return ret.commitChanges();
  }

  public static <C, V extends SszData> SszVector<V> toSszVector(
      SszVectorSchema<V, ?> type, Iterable<C> list, Function<C, V> converter) {
    return toSszVector(type, Streams.stream(list).map(converter).collect(Collectors.toList()));
  }

  public static <V extends SszData> SszVector<V> toSszVector(
      SszVectorSchema<V, ?> type, Iterable<V> list) {
    SszMutableVector<V> ret = type.getDefault().createWritableCopy();
    int idx = 0;
    for (V v : list) {
      if (idx >= type.getLength()) {
        throw new IllegalArgumentException("List size exceeds vector size");
      }
      ret.set(idx, v);
      idx++;
    }
    return ret.commitChanges();
  }

  /** Creates immutable vector of bytes with size `bytes.size()` from {@link Bytes} value */
  public static SszVector<SszByte> toSszByteVector(Bytes bytes) {
    SszVectorSchema<SszByte, ?> type = new SszByteVectorSchema(bytes.size());
    return type.sszDeserialize(SszReader.fromBytes(bytes));
  }

  public static SszVector<SszByte> toSszByteVector(SszVectorSchema<SszByte, ?> type, Bytes bytes) {
    return type.sszDeserialize(SszReader.fromBytes(bytes));
  }

  public static SszList<SszByte> toSszByteList(SszListSchema<SszByte, ?> type, Bytes bytes) {
    return type.sszDeserialize(SszReader.fromBytes(bytes));
  }

  /** Retrieve bytes from vector of bytes to a {@link Bytes} instance */
  public static Bytes getAllBytes(SszCollection<SszByte> vector) {
    return vector.sszSerialize();
  }

  /**
   * Creates immutable list of bits with size `bitlist.size()` and maxSize = `bitlist.getMaxSize()`
   * from {@link Bitlist} value
   */
  public static SszList<SszBit> toSszBitList(Bitlist bitlist) {
    return toSszBitList(SszBitlistSchema.create(bitlist.getMaxSize()), bitlist);
  }

  public static SszList<SszBit> toSszBitList(SszListSchema<SszBit, ?> type, Bitlist bitlist) {
    return type.sszDeserialize(SszReader.fromBytes(bitlist.serialize()));
  }

  /** Converts list of bits to {@link Bitlist} value */
  public static Bitlist getBitlist(SszList<SszBit> bitlistView) {
    return Bitlist.fromSszBytes(bitlistView.sszSerialize(), bitlistView.getSchema().getMaxLength());
  }

  /** Creates immutable vector of bits with size `bitvector.size()` from {@link Bitvector} value */
  public static SszVector<SszBit> toSszBitVector(Bitvector bitvector) {
    SszMutableVector<SszBit> viewWrite =
        new SszBitVectorSchema(bitvector.getSize()).getDefault().createWritableCopy();
    for (int i = 0; i < bitvector.getSize(); i++) {
      viewWrite.set(i, SszBit.viewOf(bitvector.getBit(i)));
    }
    return viewWrite.commitChanges();
  }

  /** Converts vector of bits to {@link Bitvector} value */
  public static Bitvector getBitvector(SszVector<SszBit> vectorView) {
    int[] bitIndexes =
        IntStream.range(0, vectorView.size()).filter(i -> vectorView.get(i).get()).toArray();
    return new Bitvector(vectorView.size(), bitIndexes);
  }
}
