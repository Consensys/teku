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

package tech.pegasys.teku.reference.phase0.kzg;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellID;
import tech.pegasys.teku.kzg.KZGCellWithIds;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;

public class KzgVerifyCellKzgProofBatchTestExecutor extends KzgTestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition, final KZG kzg) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final Boolean expectedVerificationResult = data.getOutput();
    Boolean actualVerificationResult;
    try {
      actualVerificationResult =
          kzg.verifyCellProofBatch(
              data.getInput().getRowCommitments(),
              IntStream.range(0, data.getInput().getCells().size())
                  .mapToObj(
                      index ->
                          new KZGCellWithIds(
                              data.getInput().getCells().get(index),
                              KZGCellID.fromCellColumnIndex(
                                  data.getInput().getRowIndices().get(index)),
                              KZGCellID.fromCellColumnIndex(
                                  data.getInput().getColumnIndices().get(index))))
                  .toList(),
              data.getInput().getProofs());
    } catch (final RuntimeException ex) {
      actualVerificationResult = null;
    }
    assertThat(actualVerificationResult).isEqualTo(expectedVerificationResult);
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private Boolean output;

    public Input getInput() {
      return input;
    }

    public Boolean getOutput() {
      return output;
    }

    private static class Input {
      @JsonProperty(value = "row_commitments", required = true)
      private List<String> rowCommitments;

      @JsonProperty(value = "row_indices", required = true)
      private List<Integer> rowIndices;

      @JsonProperty(value = "column_indices", required = true)
      private List<Integer> columnIndices;

      @JsonProperty(value = "cells", required = true)
      private List<String> cells;

      @JsonProperty(value = "proofs", required = true)
      private List<String> proofs;

      public List<KZGCommitment> getRowCommitments() {
        return rowCommitments.stream().map(KZGCommitment::fromHexString).toList();
      }

      public List<Integer> getRowIndices() {
        return rowIndices;
      }

      public List<Integer> getColumnIndices() {
        return columnIndices;
      }

      public List<KZGCell> getCells() {
        return cells.stream().map(cell -> new KZGCell(Bytes.fromHexString(cell))).toList();
      }

      public List<KZGProof> getProofs() {
        return proofs.stream().map(KZGProof::fromHexString).toList();
      }
    }
  }
}
