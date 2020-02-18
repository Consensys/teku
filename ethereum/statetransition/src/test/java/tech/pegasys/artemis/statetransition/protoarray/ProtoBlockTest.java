package tech.pegasys.artemis.statetransition.protoarray;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.statetransition.protoArray.ProtoBlock;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.statetransition.protoArray.ProtoBlock.createNewProtoBlock;

public class ProtoBlockTest {

  @Test
  void modifyWeightTest() {
    ProtoBlock protoBlock = createNewProtoBlock(
            0,
            Bytes32.fromHexString("0x01"),
            Bytes32.fromHexString("0x00"));

    assertThat(protoBlock.getWeight()).isEqualTo(UnsignedLong.ZERO);
    protoBlock.modifyWeight(10);
    assertThat(protoBlock.getWeight()).isEqualTo(UnsignedLong.valueOf(10));
    protoBlock.modifyWeight(-5);
    assertThat(protoBlock.getWeight()).isEqualTo(UnsignedLong.valueOf(5));
  }
}
