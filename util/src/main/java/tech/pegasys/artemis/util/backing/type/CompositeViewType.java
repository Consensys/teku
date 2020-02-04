package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.CompositeView;
import tech.pegasys.artemis.util.backing.Utils;
import tech.pegasys.artemis.util.backing.ViewType;

public interface CompositeViewType<V extends CompositeView> extends ViewType<V> {

  int getMaxLength();

  ViewType<?> getChildType(int index);

  int getBitsPerElement();

  default int getElementsPerChunk() {
    return 256 / getBitsPerElement();
  }

  default int maxChunks() {
    return (getMaxLength() * getBitsPerElement() - 1) / 256 + 1;
  }

  default int treeDepth() {
    return Integer.bitCount(Utils.nextPowerOf2(maxChunks()) - 1);
  }

  default int treeWidth() {
    return Utils.nextPowerOf2(maxChunks());
  }
}
