package tech.pegasys.teku.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.primitive.SszNone;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.schema.SszUnionSchema;
import tech.pegasys.teku.ssz.schema.SszUnionSchemaTest;

public class SszUnionTest implements SszDataTestBase {

  @Override
  public Stream<SszUnion> sszData() {
    RandomSszDataGenerator randomGen = new RandomSszDataGenerator();
    return SszUnionSchemaTest.testUnionSchemas()
        .flatMap(unionSchema ->
            Stream.concat(
                Stream.of(unionSchema.getDefault()),
                IntStream.range(0, unionSchema.getTypesCount())
                    .mapToObj(i -> unionSchema
                        .createFromValue(i, randomGen.randomData(unionSchema.getChildSchema(i))))
            )
        );
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  void getSelector_matchesDataSchema(SszUnion data) {
    int selector = data.getSelector();
    SszSchema<?> expectedValueSchema = data.getSchema().getChildSchema(selector);

    assertThat(data.getValue().getSchema()).isEqualTo(expectedValueSchema);
  }

  @Test
  void sszSerialize_noneUnionEqualsToZeroByte() {
    SszUnionSchema<?> schema = SszUnionSchema
        .create(List.of(SszPrimitiveSchemas.NONE_SCHEMA, SszPrimitiveSchemas.BYTES32_SCHEMA));
    SszUnion union = schema.createFromValue(0, SszNone.INSTANCE);

    assertThat(union.sszSerialize()).isEqualTo(Bytes.of(0));
  }

  @Test
  void hashTreeRoot_none() {
    SszUnionSchema<?> schema = SszUnionSchema
        .create(List.of(SszPrimitiveSchemas.NONE_SCHEMA, SszPrimitiveSchemas.BYTES32_SCHEMA));
    SszUnion union = schema.createFromValue(0, SszNone.INSTANCE);

    assertThat(union.hashTreeRoot()).isEqualTo(
        Hash.sha2_256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
  }
}
