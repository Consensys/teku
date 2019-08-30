package tech.pegasys.artemis.util.hashtree;

import org.apache.tuweni.bytes.Bytes32;

public interface SigningRoot {
  Bytes32 signing_root(String truncation_param);
}
