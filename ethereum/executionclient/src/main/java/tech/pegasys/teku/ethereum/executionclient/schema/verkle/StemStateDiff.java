/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.executionclient.schema.verkle;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes31Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes31Serializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes31;
import tech.pegasys.teku.spec.datastructures.execution.verkle.StemStateDiffSchema;
import tech.pegasys.teku.spec.datastructures.execution.verkle.SuffixStateDiffSchema;

public class StemStateDiff {

  @JsonSerialize(using = Bytes31Serializer.class)
  @JsonDeserialize(using = Bytes31Deserializer.class)
  @JsonProperty("stem")
  private final Bytes31 stem;

  @JsonProperty("suffixDiffs")
  private final List<SuffixStateDiff> suffixDiffs;

  public StemStateDiff(
      @JsonProperty("stem") final Bytes31 stem,
      @JsonProperty("suffixDiffs") final List<SuffixStateDiff> suffixDiffs) {
    this.stem = stem;
    this.suffixDiffs = suffixDiffs;
  }

  public StemStateDiff(
      final tech.pegasys.teku.spec.datastructures.execution.verkle.StemStateDiff stemStateDiff) {
    this.stem = stemStateDiff.getStem();
    this.suffixDiffs = stemStateDiff.getStateDiffs().stream().map(SuffixStateDiff::new).toList();
  }

  public tech.pegasys.teku.spec.datastructures.execution.verkle.StemStateDiff
      asInternalStemStateDiff(final StemStateDiffSchema schema) {
    return schema.create(
        stem,
        suffixDiffs.stream()
            .map(
                external ->
                    external.asInternalSuffixStateDiff(
                        (SuffixStateDiffSchema) schema.getSuffixDiffsSchema().getElementSchema()))
            .toList());
  }
}
