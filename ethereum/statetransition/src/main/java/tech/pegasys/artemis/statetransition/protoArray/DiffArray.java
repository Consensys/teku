package tech.pegasys.artemis.statetransition.protoArray;

import com.google.common.primitives.UnsignedLong;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DiffArray {
  private final List<Long> array;
  private final int arrayStartIndex;

  public DiffArray(final int arrayStartIndex, final List<Long> array) {
    this.arrayStartIndex = arrayStartIndex;
    this.array = array;
  }

  public static DiffArray createDiffArray(final int arrayStartIndex, final int size) {
    return new DiffArray(
            arrayStartIndex,
            Collections.synchronizedList(new ArrayList<>(Collections.nCopies(size, 0L)))
    );
  }

  public void noteChange(int blockIndex, Long value) {
    this.set(blockIndex, this.get(blockIndex) + value);
  }

  public long get(int blockIndex) {
    return array.get(diffArrayIndex(blockIndex));
  }

  private void set(int blockIndex, long newValue) {
    array.set(diffArrayIndex(blockIndex), newValue);
  }

  private int diffArrayIndex(int blockIndex) {
    return blockIndex - arrayStartIndex;
  }
}
