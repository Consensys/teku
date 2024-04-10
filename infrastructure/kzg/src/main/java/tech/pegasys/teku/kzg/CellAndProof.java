package tech.pegasys.teku.kzg;

public record CellAndProof(
    Cell cell,
    KZGProof proof
) {
}
