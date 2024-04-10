package tech.pegasys.teku.kzg;

import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;

import java.util.List;
import java.util.stream.IntStream;

import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_CELL;

public record Cell(Bytes bytes) {

  static Cell ZERO = new Cell(Bytes.wrap(new byte[BYTES_PER_CELL]));

  static List<Cell> splitBytes(Bytes bytes) {
    return CKZG4844Utils.bytesChunked(bytes, BYTES_PER_CELL)
        .stream()
        .map(Cell::new)
        .toList();
  }
}
