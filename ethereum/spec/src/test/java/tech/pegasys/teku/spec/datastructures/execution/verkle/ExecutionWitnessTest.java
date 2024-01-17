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

package tech.pegasys.teku.spec.datastructures.execution.verkle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionWitnessTest {
  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final SpecConfigCapella specConfigCapella =
      SpecConfigCapella.required(spec.forMilestone(SpecMilestone.CAPELLA).getConfig());
  private final SuffixStateDiffSchema suffixStateDiffSchema = SuffixStateDiffSchema.INSTANCE;
  private final StemStateDiffSchema stemStateDiffSchema =
      new StemStateDiffSchema(specConfigCapella.getVerkleWidth());
  private final IpaProofSchema ipaProofSchema =
      new IpaProofSchema(specConfigCapella.getIpaProofDepth());
  private final VerkleProofSchema verkleProofSchema =
      new VerkleProofSchema(
          ipaProofSchema,
          specConfigCapella.getMaxStems(),
          specConfigCapella.getMaxCommitmentsPerStem());
  private final ExecutionWitnessSchema executionWitnessSchema =
      new ExecutionWitnessSchema(
          specConfigCapella.getMaxStems(), stemStateDiffSchema, verkleProofSchema);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SuffixStateDiff suffixStateDiff1 =
      suffixStateDiffSchema.create(
          (byte) 13, Optional.of(dataStructureUtil.randomBytes32()), Optional.empty());
  private final SuffixStateDiff suffixStateDiff2 =
      suffixStateDiffSchema.create(
          (byte) 14, Optional.empty(), Optional.of(dataStructureUtil.randomBytes32()));
  private final SuffixStateDiff suffixStateDiff3 =
      suffixStateDiffSchema.create(
          (byte) 15,
          Optional.of(dataStructureUtil.randomBytes32()),
          Optional.of(dataStructureUtil.randomBytes32()));
  private final StemStateDiff stemStateDiff1 =
      stemStateDiffSchema.create(
          dataStructureUtil.randomBytes31(),
          List.of(suffixStateDiff1, suffixStateDiff2, suffixStateDiff3));
  private final StemStateDiff stemStateDiff2 =
      stemStateDiffSchema.create(dataStructureUtil.randomBytes31(), List.of(suffixStateDiff2));
  private final IpaProof ipaProof =
      ipaProofSchema.create(
          List.of(
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32()),
          List.of(
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32(),
              dataStructureUtil.randomBytes32()),
          dataStructureUtil.randomBytes32());
  private final VerkleProof verkleProof =
      verkleProofSchema.create(
          List.of(dataStructureUtil.randomBytes31(), dataStructureUtil.randomBytes31()),
          List.of((byte) 16, (byte) 17),
          List.of(dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32()),
          dataStructureUtil.randomBytes32(),
          ipaProof);

  @Test
  public void objectEquality() {
    final ExecutionWitness executionWitness1 =
        executionWitnessSchema.create(List.of(stemStateDiff1, stemStateDiff2), verkleProof);
    final ExecutionWitness executionWitness2 =
        executionWitnessSchema.create(List.of(stemStateDiff1, stemStateDiff2), verkleProof);

    assertThat(executionWitness1).isEqualTo(executionWitness2);
    SszDataAssert.assertThatSszData(executionWitness1).isEqualByAllMeansTo(executionWitness2);
  }

  @Test
  public void objectNonEquality() {
    final ExecutionWitness executionWitness1 =
        executionWitnessSchema.create(List.of(stemStateDiff1, stemStateDiff2), verkleProof);
    final ExecutionWitness executionWitness2 =
        executionWitnessSchema.create(List.of(stemStateDiff2, stemStateDiff1), verkleProof);

    assertThat(executionWitness1).isNotEqualTo(executionWitness2);
  }

  @Test
  public void objectAccessorMethods() {
    final ExecutionWitness executionWitness =
        executionWitnessSchema.create(List.of(stemStateDiff1, stemStateDiff2), verkleProof);

    assertThat(executionWitness.getStateDiffs()).isEqualTo(List.of(stemStateDiff1, stemStateDiff2));
    assertThat(executionWitness.getVerkleProof()).isEqualTo(verkleProof);
  }

  @Test
  public void roundTripSSZ() {
    final ExecutionWitness executionWitness =
        executionWitnessSchema.create(List.of(stemStateDiff1, stemStateDiff2), verkleProof);

    final Bytes executionWitnessBytes = executionWitness.sszSerialize();
    final ExecutionWitness deserializedObject =
        executionWitnessSchema.sszDeserialize(executionWitnessBytes);

    assertThat(executionWitness).isEqualTo(deserializedObject);
    SszDataAssert.assertThatSszData(executionWitness).isEqualByAllMeansTo(deserializedObject);
  }
}
