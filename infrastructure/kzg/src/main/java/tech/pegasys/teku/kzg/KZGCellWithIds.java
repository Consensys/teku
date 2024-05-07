/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.kzg;

public record KZGCellWithIds(KZGCell cell, KZGCellID rowId, KZGCellID columnId) {

  public static KZGCellWithIds fromCellAndIndices(KZGCell cell, int rowIndex, int columnIndex) {
    return new KZGCellWithIds(
        cell, KZGCellID.fromCellColumnIndex(rowIndex), KZGCellID.fromCellColumnIndex(columnIndex));
  }

  public static KZGCellWithIds fromKZGCellWithColumnIdAndRowId(
      KZGCellWithColumnId cellWithColumnId, int rowIndex) {
    return new KZGCellWithIds(
        cellWithColumnId.cell(),
        KZGCellID.fromCellColumnIndex(rowIndex),
        cellWithColumnId.columnId());
  }
}
