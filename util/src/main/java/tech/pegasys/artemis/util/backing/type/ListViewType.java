package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.MutableListView;
import tech.pegasys.artemis.util.backing.Utils;
import tech.pegasys.artemis.util.backing.ViewType;

public abstract class ListViewType<C, L extends MutableListView<C>> implements ViewType<L> {
  private final int maxLength;
  private final int bitsPerElement;

  public ListViewType(int maxLength, int bitsPerElement) {
    this.maxLength = maxLength;
    this.bitsPerElement = bitsPerElement;
  }

  public ListViewType(int maxLength) {
    this(maxLength, 32 * 8);
  }

  public int getBitsPerElement() {
    return bitsPerElement;
  }

  public int getElementsPerNode() {
    return 32 * 8 / getBitsPerElement();
  }

  public int getMaxLength() {
    return maxLength;
  }

  public int maxChunks() {
    return (getMaxLength() * getBitsPerElement() - 1) / 256 + 1;
  }

  public int treeDepth() {
    return Integer.bitCount(Utils.nextPowerOf2(maxChunks()) - 1);
  }

  public int treeWidth() {
    return Utils.nextPowerOf2(maxChunks());
  }
}
