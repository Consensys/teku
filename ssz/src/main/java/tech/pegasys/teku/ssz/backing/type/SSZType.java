package tech.pegasys.teku.ssz.backing.type;

public interface SSZType {
  int SSZ_OFFSET_SIZE = 4;

  boolean isFixedSize();

  int getFixedPartSize();

}
