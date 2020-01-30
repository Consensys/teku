package tech.pegasys.artemis.util.backing;

public interface CompositeViewType<V extends CompositeView> extends ViewType<V> {

  int getMaxLength();

  ViewType<?> getChildType(int index);

  default int getBitsPerElement() {
    return 256;
  }

  default int getElementsPerNode() {
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
