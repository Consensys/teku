package tech.pegasys.artemis.statetransition.protoarray;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.statetransition.protoArray.ProtoNode;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.statetransition.protoArray.ProtoNode.createNewProtoNode;

public class ProtoBlockTest {

  @Test
  void modifyWeightTest() {
    ProtoNode protoNode = createNewProtoNode(
            0,
            Bytes32.fromHexString("0x01"),
            Bytes32.fromHexString("0x00"));

    assertThat(protoNode.getWeight()).isEqualTo(UnsignedLong.ZERO);
    protoNode.modifyWeight(10);
    assertThat(protoNode.getWeight()).isEqualTo(UnsignedLong.valueOf(10));
    protoNode.modifyWeight(-5);
    assertThat(protoNode.getWeight()).isEqualTo(UnsignedLong.valueOf(5));
  }
}
