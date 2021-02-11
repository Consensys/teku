package tech.pegasys.teku.ssz.backing.collections;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableComposite;
import tech.pegasys.teku.ssz.backing.SszMutableData;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;

public interface SszBitlist extends SszList<SszBit> {

  static SszBitlist nullableOr(
      @Nullable SszBitlist bitlist1OrNull, @Nullable SszBitlist bitlist2OrNull) {
    checkArgument(
        bitlist1OrNull != null || bitlist2OrNull != null,
        "At least one argument should be non-null");
    if (bitlist1OrNull == null) {
      return bitlist2OrNull;
    } else if (bitlist2OrNull == null) {
      return bitlist1OrNull;
    } else {
      return bitlist1OrNull.or(bitlist2OrNull);
    }
  }

  @Override
  default SszMutableList<SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }



  @Deprecated
  Bitlist toLegacy();

  @Override
  SszBitlistSchema<SszBitlist> getSchema();

  SszBitlist or(SszBitlist other);

  boolean getBit(int i);

  int getBitCount();

  boolean intersects(SszBitlist other);

  boolean isSuperSetOf(final SszBitlist other);

  List<Integer> getAllSetBits();

  IntStream streamAllSetBits();

  int getSize();
}
