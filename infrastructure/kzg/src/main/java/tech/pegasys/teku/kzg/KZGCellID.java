package tech.pegasys.teku.kzg;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record KZGCellID(UInt64 id) {

  static KZGCellID fromCellColumnIndex(int idx) {
    return new KZGCellID(UInt64.valueOf(idx));
  }

  int getColumnIndex() {
    return id.intValue();
  }
}
