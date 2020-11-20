package tech.pegasys.teku.ssz.backing.tree;

public class GIndexUtil {

  final static long LEFTMOST_G_INDEX = gIdxLeftmostFrom(1);
  final static long RIGHTMOST_G_INDEX = gIdxRightmostFrom(1);

  public static boolean gIdxIsSelf(long generalizedIndex) {
    return generalizedIndex == 1;
  }

  public static long gIdxLeftmostFrom(long fromGeneralizedIndex) {
    return fromGeneralizedIndex << (63 - TreeUtil.treeDepth(fromGeneralizedIndex));
  }

  public static long gIdxRightmostFrom(long fromGeneralizedIndex) {
    int shiftN = 63 - TreeUtil.treeDepth(fromGeneralizedIndex);
    return (fromGeneralizedIndex << shiftN) | ((1L << shiftN) - 1);
  }

  public static int gIdxGetChildIndex(long generalizedIndex, int childDepth) {
    long anchor = Long.highestOneBit(generalizedIndex);
    int indexBitCount = Long.bitCount(anchor - 1);
    if (indexBitCount < childDepth) {
      throw new IllegalArgumentException(
          "Generalized index " + generalizedIndex + " is upper than depth " + childDepth);
    }
    return (int) (generalizedIndex >>> (indexBitCount - childDepth));
  }

  public static long gIdxGetRelativeGIndex(long generalizedIndex, int childDepth) {
    long anchor = Long.highestOneBit(generalizedIndex);
    long pivot = anchor >>> childDepth;
    if (pivot == 0) {
      throw new IllegalArgumentException(
          "Generalized index " + generalizedIndex + " is upper than depth " + childDepth);
    }
    return generalizedIndex & (pivot - 1) | pivot;
  }
}
