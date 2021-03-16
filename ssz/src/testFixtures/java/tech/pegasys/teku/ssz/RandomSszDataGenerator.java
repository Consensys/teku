/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.ssz;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.primitive.SszBit;
import tech.pegasys.teku.ssz.primitive.SszByte;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszBytes4;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.ssz.type.Bytes4;

public class RandomSszDataGenerator {
  private final Supplier<SszBit> bitSupplier;
  private final Supplier<SszByte> byteSupplier;
  private final Supplier<SszBytes4> bytes4Supplier;
  private final Supplier<SszUInt64> uintSupplier;
  private final Supplier<SszBytes32> bytes32Supplier;

  private int maxListSize = 16 * 1024;

  public RandomSszDataGenerator() {
    this(new Random(1));
  }

  public RandomSszDataGenerator(Random random) {
    bitSupplier = () -> SszBit.of(random.nextBoolean());
    byteSupplier = () -> SszByte.of(random.nextInt());
    bytes4Supplier = () -> SszBytes4.of(Bytes4.rightPad(Bytes.random(4, random)));
    uintSupplier = () -> SszUInt64.of(UInt64.fromLongBits(random.nextLong()));
    bytes32Supplier = () -> SszBytes32.of(Bytes32.random(random));
  }

  public RandomSszDataGenerator withMaxListSize(int maxListSize) {
    this.maxListSize = maxListSize;
    return this;
  }

  public <T extends SszData> T randomData(SszSchema<T> schema) {
    return randomDataStream(schema).findFirst().orElseThrow();
  }

  @SuppressWarnings("unchecked")
  public <T extends SszData> Stream<T> randomDataStream(SszSchema<T> schema) {
    if (schema instanceof AbstractSszPrimitiveSchema) {
      if (schema == SszPrimitiveSchemas.BIT_SCHEMA) {
        return (Stream<T>) Stream.generate(bitSupplier);
      } else if (schema == SszPrimitiveSchemas.BYTE_SCHEMA) {
        return (Stream<T>) Stream.generate(byteSupplier);
      } else if (schema == SszPrimitiveSchemas.UINT64_SCHEMA) {
        return (Stream<T>) Stream.generate(uintSupplier);
      } else if (schema == SszPrimitiveSchemas.BYTES4_SCHEMA) {
        return (Stream<T>) Stream.generate(bytes4Supplier);
      } else if (schema == SszPrimitiveSchemas.BYTES32_SCHEMA) {
        return (Stream<T>) Stream.generate(bytes32Supplier);
      } else {
        throw new IllegalArgumentException("Unknown primitive schema: " + schema);
      }
    } else if (schema instanceof AbstractSszContainerSchema) {
      AbstractSszContainerSchema<SszContainer> containerSchema =
          (AbstractSszContainerSchema<SszContainer>) schema;
      return Stream.generate(
          () -> {
            List<SszData> children =
                containerSchema.getFieldSchemas().stream()
                    .map(this::randomData)
                    .collect(Collectors.toList());
            return (T) containerSchema.createFromFieldValues(children);
          });
    } else if (schema instanceof SszCollectionSchema) {
      return Stream.generate(
          () -> {
            SszCollectionSchema<SszData, ?> collectionSchema =
                (SszCollectionSchema<SszData, ?>) schema;
            SszSchema<SszData> elementSchema = collectionSchema.getElementSchema();
            int maxChildrenToAdd = (int) Long.min(collectionSchema.getMaxLength(), maxListSize);
            List<SszData> children =
                Stream.generate(() -> randomData(elementSchema))
                    .limit(maxChildrenToAdd)
                    .collect(Collectors.toList());
            SszCollection<SszData> ret = collectionSchema.createFromElements(children);
            return (T) ret;
          });
    } else {
      throw new IllegalArgumentException("Unknown schema: " + schema);
    }
  }
}
