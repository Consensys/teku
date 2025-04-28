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
import com.google.common.collect.Streams;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.kzg.KZGProof;

public class KzgComputeCellsAndKzgProofsTestExecutor extends KzgTestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition, final KZG kzg) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final List<KZGCellAndProof> expectedKzgCellsAndProofs = data.getOutput();
    List<KZGCellAndProof> actualKzgCellsAndProofs;
    try {
      final Bytes blob = data.getInput().getBlob();
      actualKzgCellsAndProofs = kzg.computeCellsAndProofs(blob);
    } catch (final RuntimeException ex) {
      actualKzgCellsAndProofs = null;
    }
    assertThat(actualKzgCellsAndProofs).isEqualTo(expectedKzgCellsAndProofs);
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private List<String>[] output;

    public Input getInput() {
      return input;
    }

    public List<KZGCellAndProof> getOutput() {
      return output == null
          ? null
          : Streams.zip(
                  output[0].stream()
                      .map(cellString -> new KZGCell(Bytes.fromHexString(cellString))),
                  output[1].stream().map(KZGProof::fromHexString),
                  KZGCellAndProof::new)
              .toList();
    }

    private static class Input {
      @JsonProperty(value = "blob", required = true)
      private String blob;

      public Bytes getBlob() {
        return Bytes.fromHexString(blob);
      }
    }
  }
}
