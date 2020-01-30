package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.CompositeViewType;
import tech.pegasys.artemis.util.backing.ListView;

public abstract class ListViewType<C, L extends ListView<C>> implements
    CompositeViewType<L> {
  private final int maxLength;
  private final int bitsPerElement;

  public ListViewType(int maxLength, int bitsPerElement) {
    this.maxLength = maxLength;
    this.bitsPerElement = bitsPerElement;
  }

  public ListViewType(int maxLength) {
    this(maxLength, 32 * 8);
  }

  @Override
  public int getBitsPerElement() {
    return bitsPerElement;
  }

  public int getMaxLength() {
    return maxLength;
  }

}
