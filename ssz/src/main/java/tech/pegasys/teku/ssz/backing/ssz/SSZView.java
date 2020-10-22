package tech.pegasys.teku.ssz.backing.ssz;

import java.util.Iterator;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;

public interface SSZView {

  Bytes getAllBytes();

  default int getSize() {
    return getAllBytes().size();
  }

  default Iterator<Bytes> iterator() {
    return List.of(getAllBytes()).iterator();
  }
}
