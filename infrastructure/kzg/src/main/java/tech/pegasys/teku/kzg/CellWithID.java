package tech.pegasys.teku.kzg;

public record CellWithID(
    Cell cell,
    CellID id
) {

  static CellWithID fromCellAndColumn(Cell cell, int index) {
    return new CellWithID(cell, CellID.fromCellColumnIndex(index));
  }
}
