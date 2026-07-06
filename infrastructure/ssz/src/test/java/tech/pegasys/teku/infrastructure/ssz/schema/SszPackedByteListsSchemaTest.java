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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNodeTest.elementsOfSizes;
import static tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNodeTest.serializeElements;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszPackedByteListsNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszPackedByteListsSchemaTest {

  private static final SszByteListSchema<SszByteList> ELEMENT_SCHEMA =
      SszByteListSchema.create(256);

  private static final SszListSchema<SszByteList, ?> HINTED =
      SszListSchema.create(ELEMENT_SCHEMA, 16, SszSchemaHints.sszPackedByteLists());
  private static final SszListSchema<SszByteList, ?> UNHINTED =
      SszListSchema.create(ELEMENT_SCHEMA, 16);

  @Test
  public void hint_shouldBeAcceptedForByteListElements() {
    final SszListSchema<SszByteList, ?> schema =
        SszListSchema.create(ELEMENT_SCHEMA, 16, SszSchemaHints.sszPackedByteLists());
    assertThat(schema.toString()).contains("SszPackedByteListsHint");
  }

  @Test
  public void hint_shouldBeRejectedForNonByteListElements() {
    assertThatThrownBy(
            () ->
                SszListSchema.create(
                    SszPrimitiveSchemas.UINT64_SCHEMA, 16, SszSchemaHints.sszPackedByteLists()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Bytes serialized(final int... sizes) {
    return serializeElements(elementsOfSizes(sizes));
  }

  @SuppressWarnings("unchecked")
  private static SszList<SszByteList> deserializeHinted(final Bytes bytes) {
    return (SszList<SszByteList>) HINTED.sszDeserialize(bytes);
  }

  @SuppressWarnings("unchecked")
  private static SszList<SszByteList> deserializeUnhinted(final Bytes bytes) {
    return (SszList<SszByteList>) UNHINTED.sszDeserialize(bytes);
  }

  private static TreeNode vectorNode(final SszList<SszByteList> list) {
    return list.getBackingNode().get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  @Test
  public void deserialize_shouldProducePackedNodeAndMatchOracle() {
    final Bytes bytes = serialized(1, 0, 33, 100);
    final SszList<SszByteList> hinted = deserializeHinted(bytes);
    final SszList<SszByteList> unhinted = deserializeUnhinted(bytes);
    assertThat(vectorNode(hinted)).isInstanceOf(SszPackedByteListsNode.class);
    assertThat(hinted.hashTreeRoot()).isEqualTo(unhinted.hashTreeRoot());
    assertThat(hinted.size()).isEqualTo(4);
    for (int i = 0; i < hinted.size(); i++) {
      assertThat(hinted.get(i).getBytes()).isEqualTo(unhinted.get(i).getBytes());
    }
  }

  @Test
  public void deserialize_shouldProduceDefaultTreeForEmptyList() {
    final SszList<SszByteList> hinted = deserializeHinted(Bytes.EMPTY);
    assertThat(vectorNode(hinted)).isNotInstanceOf(SszPackedByteListsNode.class);
    assertThat(hinted.hashTreeRoot())
        .isEqualTo(UNHINTED.sszDeserialize(Bytes.EMPTY).hashTreeRoot());
  }

  @Test
  public void deserialize_shouldRejectMalformedInputLikeOracle() {
    // too many elements (17 > 16)
    final Bytes tooMany = serialized(IntStream.range(0, 17).map(i -> 1).toArray());
    assertThatThrownBy(() -> HINTED.sszDeserialize(tooMany))
        .isInstanceOf(SszDeserializeException.class);
    assertThatThrownBy(() -> UNHINTED.sszDeserialize(tooMany))
        .isInstanceOf(SszDeserializeException.class);
    // element exceeding the inner limit (257 > 256)
    final Bytes overlongElement = serialized(257);
    assertThatThrownBy(() -> HINTED.sszDeserialize(overlongElement))
        .isInstanceOf(SszDeserializeException.class);
    assertThatThrownBy(() -> UNHINTED.sszDeserialize(overlongElement))
        .isInstanceOf(SszDeserializeException.class);
    // misaligned first offset
    final Bytes misaligned = Bytes.concatenate(Bytes.of(5, 0, 0, 0), Bytes.of(1));
    assertThatThrownBy(() -> HINTED.sszDeserialize(misaligned))
        .isInstanceOf(SszDeserializeException.class);
    // non-monotonic offsets: two elements, second offset before first
    final Bytes nonMonotonic =
        Bytes.concatenate(Bytes.of(8, 0, 0, 0), Bytes.of(7, 0, 0, 0), Bytes.of(1, 2));
    assertThatThrownBy(() -> HINTED.sszDeserialize(nonMonotonic))
        .isInstanceOf(SszDeserializeException.class);
    // zero first offset: rejected (deliberate deviation; matches the progressive-list fix)
    final Bytes zeroFirstOffset = Bytes.of(0, 0, 0, 0);
    assertThatThrownBy(() -> HINTED.sszDeserialize(zeroFirstOffset))
        .isInstanceOf(SszDeserializeException.class);
    // truncated input shorter than a single offset (1-3 bytes)
    for (final Bytes truncated : List.of(Bytes.of(1), Bytes.of(1, 2), Bytes.of(1, 2, 3))) {
      assertThatThrownBy(() -> HINTED.sszDeserialize(truncated))
          .describedAs("hinted, %s bytes", truncated.size())
          .isInstanceOf(SszDeserializeException.class);
      assertThatThrownBy(() -> UNHINTED.sszDeserialize(truncated))
          .describedAs("unhinted, %s bytes", truncated.size())
          .isInstanceOf(SszDeserializeException.class);
    }
  }

  @Test
  public void serialize_shouldRoundTripByteIdentical() {
    final Bytes bytes = serialized(1, 0, 33, 100);
    final SszList<SszByteList> hinted = (SszList<SszByteList>) HINTED.sszDeserialize(bytes);
    assertThat(hinted.sszSerialize()).isEqualTo(bytes);
    // a decayed (updated) list must still serialize correctly through the generic path
    final SszList<SszByteList> unhinted = (SszList<SszByteList>) UNHINTED.sszDeserialize(bytes);
    assertThat(hinted.sszSerialize()).isEqualTo(unhinted.sszSerialize());
  }

  @Test
  public void createFromElements_shouldProducePackedNodeAndMatchOracle() {
    final List<SszByteList> elements =
        List.of(
            ELEMENT_SCHEMA.fromBytes(Bytes.of(1)),
            ELEMENT_SCHEMA.fromBytes(Bytes.EMPTY),
            ELEMENT_SCHEMA.fromBytes(Bytes.wrap(new byte[100])));
    final SszList<SszByteList> hinted = (SszList<SszByteList>) HINTED.createFromElements(elements);
    final SszList<SszByteList> unhinted =
        (SszList<SszByteList>) UNHINTED.createFromElements(elements);
    assertThat(vectorNode(hinted)).isInstanceOf(SszPackedByteListsNode.class);
    assertThat(hinted.hashTreeRoot()).isEqualTo(unhinted.hashTreeRoot());
    assertThat(hinted.sszSerialize()).isEqualTo(unhinted.sszSerialize());
  }

  @Test
  public void createFromElements_shouldHandleEmptyList() {
    assertThat(HINTED.createFromElements(List.of()).hashTreeRoot())
        .isEqualTo(UNHINTED.createFromElements(List.of()).hashTreeRoot());
  }
}
