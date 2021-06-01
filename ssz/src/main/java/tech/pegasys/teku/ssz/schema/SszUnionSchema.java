package tech.pegasys.teku.ssz.schema;

import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszUnion;
import tech.pegasys.teku.ssz.schema.impl.SszUnionSchemaImpl;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;

public interface SszUnionSchema<SszUnionT extends SszUnion> extends SszSchema<SszUnionT> {
  int SELECTOR_SIZE_BYTES = 1;
  int NONE_VALUE_SELECTOR = 0;

  static SszUnionSchema<?> create(List<SszSchema<?>> childrenSchemas) {
    return new SszUnionSchemaImpl(childrenSchemas);
  }

  List<SszSchema<?>> getChildrenSchemas();

  /**
   * Returns the child schema for selector.
   *
   * @throws IndexOutOfBoundsException if selector >= getTypesCount
   */
  default SszSchema<?> getChildSchema(int selector) {
    return getChildrenSchemas().get(selector);
  }

  default int getTypesCount() {
    return getChildrenSchemas().size();
  }

  SszUnionT createFromValue(int selector, SszData value);

  @Override
  default boolean isFixedSize() {
    return false;
  }

  @Override
  default int getSszFixedPartSize() {
    return SELECTOR_SIZE_BYTES;
  }

  @Override
  default SszLengthBounds getSszLengthBounds() {
    return IntStream.range(0, getTypesCount())
        .mapToObj(this::getChildSchema)
        .map(SszType::getSszLengthBounds)
        .reduce(SszLengthBounds::or)
        .orElseThrow()
        .addBytes(SELECTOR_SIZE_BYTES)
        .ceilToBytes();
  }
}
