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
import tech.pegasys.teku.kzg.KZGCellWithColumnId;

public class KzgRecoverAllCellsTestExecutor extends KzgTestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition, final KZG kzg) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final List<KZGCell> expectedKzgCells = data.getOutput();
    List<KZGCell> actualKzgCells;
    try {
      final List<Integer> cellIds = data.getInput().getCellIds();
      final List<KZGCell> cells = data.getInput().getCells();
      if (cells.size() != cellIds.size()) {
        throw new RuntimeException("Cells doesn't match ids");
      }
      final List<KZGCellWithColumnId> cellWithIds =
          Streams.zip(cells.stream(), cellIds.stream(), KZGCellWithColumnId::fromCellAndColumn)
              .toList();
      actualKzgCells = kzg.recoverCells(cellWithIds);
    } catch (final RuntimeException ex) {
      actualKzgCells = null;
    }
    assertThat(actualKzgCells).isEqualTo(expectedKzgCells);
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private List<String> output;

    public Input getInput() {
      return input;
    }

    public List<KZGCell> getOutput() {
      return output == null
          ? null
          : output.stream()
              .map(cellString -> new KZGCell(Bytes.fromHexString(cellString)))
              .toList();
    }

    private static class Input {
      @JsonProperty(value = "cell_ids", required = true)
      private List<Integer> cellIds;

      @JsonProperty(value = "cells", required = true)
      private List<String> cells;

      public List<Integer> getCellIds() {
        return cellIds;
      }

      public List<KZGCell> getCells() {
        return cells.stream()
            .map(cellString -> new KZGCell(Bytes.fromHexString(cellString)))
            .toList();
      }
    }
  }
}
