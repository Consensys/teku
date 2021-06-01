package tech.pegasys.teku.ssz.schema;

import java.util.List;
import java.util.stream.Stream;

public class SszUnionSchemaTest implements SszSchemaTestBase {

  public static Stream<SszUnionSchema<?>> testUnionSchemas() {
    return Stream.of(
        SszUnionSchema.create(List.of(
            SszPrimitiveSchemas.NONE_SCHEMA,
            SszPrimitiveSchemas.BIT_SCHEMA)),
        SszUnionSchema.create(List.of(
            SszPrimitiveSchemas.NONE_SCHEMA,
            SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32))),
        SszUnionSchema.create(List.of(
            SszPrimitiveSchemas.BIT_SCHEMA,
            SszPrimitiveSchemas.BYTES32_SCHEMA)),
        SszUnionSchema.create(List.of(
            SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32),
            SszPrimitiveSchemas.BYTES32_SCHEMA)),
        SszUnionSchema.create(List.of(
            SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 16),
            SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 32))),
        SszUnionSchema.create(List.of(
            SszPrimitiveSchemas.BIT_SCHEMA,
            SszPrimitiveSchemas.BYTES4_SCHEMA,
            SszPrimitiveSchemas.BYTES32_SCHEMA)),
        SszUnionSchema.create(List.of(
            SszPrimitiveSchemas.BIT_SCHEMA,
            SszPrimitiveSchemas.BYTES4_SCHEMA,
            SszPrimitiveSchemas.BIT_SCHEMA))
        );
  }

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return testUnionSchemas();
  }
}
