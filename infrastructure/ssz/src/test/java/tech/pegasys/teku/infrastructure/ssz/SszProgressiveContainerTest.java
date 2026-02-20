/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszProgressiveContainerTest implements SszCompositeTestBase {

  private static final SszProgressiveContainerSchema<SszContainer> ALL_FIXED_SCHEMA =
      new SszProgressiveContainerSchema<>(
          "AllFixed",
          new boolean[] {true, true, true},
          NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
          NamedSchema.of("b", SszPrimitiveSchemas.BYTE_SCHEMA),
          NamedSchema.of("c", SszPrimitiveSchemas.UINT64_SCHEMA));

  private static final SszProgressiveContainerSchema<SszContainer> MIXED_SCHEMA =
      new SszProgressiveContainerSchema<>(
          "Mixed",
          new boolean[] {true, true},
          NamedSchema.of("fixed", SszPrimitiveSchemas.UINT64_SCHEMA),
          NamedSchema.of("var", SszBitlistSchema.create(100)));

  private static final SszProgressiveContainerSchema<SszContainer> GAPPED_SCHEMA =
      new SszProgressiveContainerSchema<>(
          "Gapped",
          new boolean[] {true, false, true},
          NamedSchema.of("first", SszPrimitiveSchemas.UINT64_SCHEMA),
          NamedSchema.of("third", SszPrimitiveSchemas.BYTE_SCHEMA));

  @Override
  public Stream<SszContainer> sszData() {
    return Stream.of(
        // All-fixed default
        ALL_FIXED_SCHEMA.createFromBackingNode(ALL_FIXED_SCHEMA.getDefaultTree()),
        // All-fixed with values
        createAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3)),
        createAllFixed(UInt64.valueOf(100), (byte) 0, UInt64.valueOf(200)),
        // Mixed default
        MIXED_SCHEMA.createFromBackingNode(MIXED_SCHEMA.getDefaultTree()),
        // Mixed with values
        createMixed(UInt64.valueOf(5), 10, 0, 3, 9),
        // Gapped default
        GAPPED_SCHEMA.createFromBackingNode(GAPPED_SCHEMA.getDefaultTree()),
        // Gapped with values
        createGapped(UInt64.valueOf(42), (byte) 7));
  }

  @Test
  void s() {
    var squareSchema =
        new SszProgressiveContainerSchema<>(
            "Square",
            new boolean[] {true, false, true},
            NamedSchema.of("side", SszPrimitiveSchemas.UINT64_SCHEMA),
            NamedSchema.of("color", SszPrimitiveSchemas.UINT8_SCHEMA));

    final TreeNode tree =
        squareSchema.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(3)), SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 2)));
    var a = squareSchema.createFromBackingNode(tree);
    var b = a.getSchema().getChildGeneralizedIndex(0);
  }

  private static SszContainer createAllFixed(final UInt64 a, final byte b, final UInt64 c) {
    final TreeNode tree =
        ALL_FIXED_SCHEMA.createTreeFromFieldValues(
            List.of(SszUInt64.of(a), SszPrimitiveSchemas.BYTE_SCHEMA.boxed(b), SszUInt64.of(c)));
    return ALL_FIXED_SCHEMA.createFromBackingNode(tree);
  }

  private static SszContainer createMixed(
      final UInt64 fixedVal, final int bitlistSize, final int... setBits) {
    final TreeNode tree =
        MIXED_SCHEMA.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(fixedVal), SszBitlistSchema.create(100).ofBits(bitlistSize, setBits)));
    return MIXED_SCHEMA.createFromBackingNode(tree);
  }

  private static SszContainer createGapped(final UInt64 first, final byte third) {
    final TreeNode tree =
        GAPPED_SCHEMA.createTreeFromFieldValues(
            List.of(SszUInt64.of(first), SszPrimitiveSchemas.BYTE_SCHEMA.boxed(third)));
    return GAPPED_SCHEMA.createFromBackingNode(tree);
  }

  @Test
  void fieldAccess_allFixed() {
    final SszContainer container =
        createAllFixed(UInt64.valueOf(10), (byte) 20, UInt64.valueOf(30));
    assertThat(((SszUInt64) container.get(0)).get()).isEqualTo(UInt64.valueOf(10));
    assertThat(((SszByte) container.get(1)).get()).isEqualTo((byte) 20);
    assertThat(((SszUInt64) container.get(2)).get()).isEqualTo(UInt64.valueOf(30));
  }

  @Test
  void fieldAccess_gapped() {
    final SszContainer container = createGapped(UInt64.valueOf(99), (byte) 3);
    assertThat(((SszUInt64) container.get(0)).get()).isEqualTo(UInt64.valueOf(99));
    assertThat(((SszByte) container.get(1)).get()).isEqualTo((byte) 3);
  }

  @Test
  void createWritableCopy_shouldSucceed() {
    final SszContainer container = createAllFixed(UInt64.ONE, (byte) 0, UInt64.ZERO);
    assertThat(container.createWritableCopy()).isNotNull();
  }

  @Test
  void isWritableSupported_shouldReturnTrue() {
    final SszContainer container = createAllFixed(UInt64.ONE, (byte) 0, UInt64.ZERO);
    assertThat(container.isWritableSupported()).isTrue();
  }

  @Test
  void toString_shouldIncludeContainerNameAndFieldValues() {
    final SszContainer container = createAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3));
    final String str = container.toString();
    assertThat(str).contains("AllFixed");
    assertThat(str).contains("a=");
    assertThat(str).contains("b=");
    assertThat(str).contains("c=");
  }
}
