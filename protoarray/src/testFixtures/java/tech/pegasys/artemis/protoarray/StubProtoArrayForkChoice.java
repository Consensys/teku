package tech.pegasys.artemis.protoarray;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StubProtoArrayForkChoice {

  public Bytes32 findHead(
          UnsignedLong justifiedEpoch,
          Bytes32 justifiedRoot,
          UnsignedLong finalizedEpoch,
          List<UnsignedLong> justifiedStateBalances) {
    return Bytes32.ZERO;
  }

  public void processAttestation(
          int validatorIndex,
          Bytes32 blockRoot,
          UnsignedLong targetEpoch) {}

  public void processBlock(
          UnsignedLong blockSlot,
          Bytes32 blockRoot,
          Bytes32 parentRoot,
          Bytes32 stateRoot,
          UnsignedLong justifiedEpoch,
          UnsignedLong finalizedEpoch) {}
}
