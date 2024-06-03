package tech.pegasys.teku.infrastructure.ssz.schema;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableProfileSchema.NamedIndexedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AbstractSszStableContainerSchemaTest {


  static class StableContainer extends AbstractSszImmutableContainer {
    protected StableContainer(SszContainerSchema<? extends AbstractSszImmutableContainer> schema, TreeNode backingNode) {
      super(schema, backingNode);
    }
  }

  static class StableContainerSchema extends AbstractSszStableContainerSchema<StableContainer> {

    public StableContainerSchema(String name, List<NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount) {
      super(name, childrenSchemas, maxFieldCount);
    }

    @Override
    public StableContainer createFromBackingNode(TreeNode node) {
      return new StableContainer(this, node);
    }
  }

  @Test
  void sanityTest() {
    StableContainerSchema squareProfileSchema = new StableContainerSchema(
        "Square",
        List.of(
            new NamedIndexedSchema<>("side", 0, SszPrimitiveSchemas.UINT64_SCHEMA),
            new NamedIndexedSchema<>("color", 1, SszPrimitiveSchemas.UINT8_SCHEMA)),
        4);

    StableContainerSchema circleProfileSchema = new StableContainerSchema(
        "Circle",
        List.of(
            new NamedIndexedSchema<>("color", 1, SszPrimitiveSchemas.UINT8_SCHEMA),
            new NamedIndexedSchema<>("radius", 2, SszPrimitiveSchemas.UINT64_SCHEMA)),
        4);

    StableContainer square = squareProfileSchema.createFromFieldValues(
        List.of(
            SszUInt64.of(UInt64.valueOf(0x42)), SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)));

    StableContainer circle =
        circleProfileSchema.createFromFieldValues(
            List.of(
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1),
                SszUInt64.of(UInt64.valueOf(0x42))));

    System.out.println(square.sszSerialize());
    System.out.println(circle.sszSerialize());
  }
}
