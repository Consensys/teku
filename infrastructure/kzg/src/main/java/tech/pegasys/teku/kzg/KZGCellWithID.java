package tech.pegasys.teku.kzg;

public record KZGCellWithID(
    KZGCell cell,
    KZGCellID id
) {

  static KZGCellWithID fromCellAndColumn(KZGCell cell, int index) {
    return new KZGCellWithID(cell, KZGCellID.fromCellColumnIndex(index));
  }
}
