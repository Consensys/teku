package tech.pegasys.teku.ssz.backing.tree;

import static java.lang.Integer.min;

public class GIndexUtil {

  public enum NodeRelation {
    Left,
    Right,
    Successor,
    Predecessor,
    Same
  }

  final static long SELF_G_INDEX = 1;
  final static long LEFTMOST_G_INDEX = gIdxLeftmostFrom(SELF_G_INDEX);
  final static long RIGHTMOST_G_INDEX = gIdxRightmostFrom(SELF_G_INDEX);

  public static boolean gIdxIsSelf(long generalizedIndex) {
    return generalizedIndex == SELF_G_INDEX;
  }

  /**
   * How idx1 relates to idx2:
   *  Left: idx1 is to the left of idx2
   *  Right: idx1 is to the right of idx2
   *  Subset: idx1 is to the right of idx2
   *  Successor: idx1 is successor of idx2
   *  Predecessor: idx1 is predecessor of idx2
   * @param idx1
   * @param idx2
   * @return
   */
  public static NodeRelation gIdxCompare(long idx1, long idx2) {
    long anchor1 = Long.highestOneBit(idx1);
    long anchor2 = Long.highestOneBit(idx2);
    int depth1 = Long.bitCount(anchor1 - 1);
    int depth2 = Long.bitCount(anchor2 - 1);
    int minDepth = min(depth1, depth2);
    long minDepthIdx1 = idx1 >>> (depth1 - minDepth);
    long minDepthIdx2 = idx2 >>> (depth2 - minDepth);
    if (minDepthIdx1 == minDepthIdx2) {
      if (depth1 < depth2) {
        return NodeRelation.Predecessor;
      } else if (depth1 > depth2) {
        return NodeRelation.Successor;
      } else {
        return NodeRelation.Same;
      }
    } else {
      if (minDepthIdx1 < minDepthIdx2) {
        return NodeRelation.Left;
      } else {
        return NodeRelation.Right;
      }
    }
  }

  public static long gIdxLeftGIndex(long generalizedIndex) {
    return gIdxChildGIndex(generalizedIndex, 0, 1);
  }

  public static long gIdxRightGIndex(long generalizedIndex) {
    return gIdxChildGIndex(generalizedIndex, 1, 1);
  }

  public static long gIdxChildGIndex(long generalizedIndex, int childIdx, int childDepth) {
    assert childIdx < (1 << childDepth);
    return (generalizedIndex << childDepth) | childIdx;
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
    long generalizedIndexWithoutAnchor = generalizedIndex ^ anchor;
    return (int) (generalizedIndexWithoutAnchor >>> (indexBitCount - childDepth));
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
