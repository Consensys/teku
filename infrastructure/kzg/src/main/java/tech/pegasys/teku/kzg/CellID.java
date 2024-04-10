package tech.pegasys.teku.kzg;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record CellID(UInt64 id) {

  static CellID fromCellColumnIndex(int idx) {
    return new CellID(UInt64.valueOf(idx));
  }

  int getColumnIndex() {
    return id.intValue();
  }
}
