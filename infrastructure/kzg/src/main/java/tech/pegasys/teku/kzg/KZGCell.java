package tech.pegasys.teku.kzg;

import org.apache.tuweni.bytes.Bytes;

import java.util.List;

import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_CELL;

public record KZGCell(Bytes bytes) {

  static KZGCell ZERO = new KZGCell(Bytes.wrap(new byte[BYTES_PER_CELL]));

  static List<KZGCell> splitBytes(Bytes bytes) {
    return CKZG4844Utils.bytesChunked(bytes, BYTES_PER_CELL)
        .stream()
        .map(KZGCell::new)
        .toList();
  }
}
