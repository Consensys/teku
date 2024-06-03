package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszStableProfileSchema<C extends SszContainer>
    extends AbstractSszContainerSchema<C> implements SszStableContainerSchema<C> {

  private final List<NamedIndexedSchema<?>> childrenActiveSchemas;

  public record NamedIndexedSchema<T extends SszData>(
      String name, int index, SszSchema<T> schema) {

    public NamedSchema<T> toNamedSchema() {
      return NamedSchema.of(name, schema);
    }
  }

  public AbstractSszStableProfileSchema(
      String name, List<NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount) {
    super(name, createAllSchemas(childrenSchemas, maxFieldCount));

    this.childrenActiveSchemas = childrenSchemas;
  }

  @Override
  public TreeNode createTreeFromFieldValues(final List<? extends SszData> fieldValues) {
    checkArgument(fieldValues.size() == childrenActiveSchemas.size(), "Wrong number of filed values");
    List<SszData> allFields = Stream.generate(() -> SszNone.INSTANCE).limit(getMaxLength()).collect(Collectors.toList());
    for(int i = 0; i < childrenActiveSchemas.size(); i++) {
      allFields.set(childrenActiveSchemas.get(i).index(), fieldValues.get(i));
    }
    return super.createTreeFromFieldValues(allFields);
  }

  private static List<? extends NamedSchema<?>> createAllSchemas(List<? extends NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount){
    Map<Integer, NamedSchema<?>> childrenSchemasByIndex = childrenSchemas.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                NamedIndexedSchema::index, NamedIndexedSchema::toNamedSchema));
    checkArgument(childrenSchemasByIndex.size() == childrenSchemas.size());
    checkArgument(childrenSchemasByIndex.keySet().stream().allMatch(i -> i < maxFieldCount));

    List<? extends NamedSchema<?>> allChildrenSchemas = IntStream.range(0, maxFieldCount)
        .mapToObj(
            idx ->
                childrenSchemasByIndex.getOrDefault(
                    idx, NamedSchema.of("__none_" + idx, SszPrimitiveSchemas.NONE_SCHEMA)))
        .toList();
    return allChildrenSchemas;
  }
}
