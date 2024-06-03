package tech.pegasys.teku.infrastructure.ssz.schema.impl;


import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszStableContainerSchema<C extends SszContainer>
    extends AbstractSszStableProfileSchema<C> {

  private final SszBitvectorSchema<SszBitvector> serializeBitvectorSchema;
  private final SszBitvector serializeBitvector;

  public AbstractSszStableContainerSchema(
      String name, List<NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount) {
    super(name, childrenSchemas, maxFieldCount);

    serializeBitvectorSchema = SszBitvectorSchema.create(maxFieldCount);
    serializeBitvector = serializeBitvectorSchema.ofBits(
        childrenSchemas.stream().mapToInt(NamedIndexedSchema::index).toArray());
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int size1 = serializeBitvector.sszSerialize(writer);
    int size2 = super.sszSerializeTree(node, writer);
    return size1 + size2;
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) {
    SszBitvector bitvector = serializeBitvectorSchema.sszDeserialize(reader);
    if (!bitvector.equals(serializeBitvector)) {
      throw new SszDeserializeException(
          "Invalid StableContainer bitvector: "
              + bitvector
              + ", expected "
              + serializeBitvector
              + " for the stable container of type "
              + this);
    }
    return super.sszDeserializeTree(reader);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return super.getSszLengthBounds().add(serializeBitvectorSchema.getSszLengthBounds());
  }

  @Override
  public int getSszSize(TreeNode node) {
    return super.getSszSize(node) + serializeBitvectorSchema.getSszFixedPartSize();
  }
}
