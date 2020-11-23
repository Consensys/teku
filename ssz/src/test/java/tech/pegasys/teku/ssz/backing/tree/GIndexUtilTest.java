package tech.pegasys.teku.ssz.backing.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxLeftGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxRightGIndex;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class GIndexUtilTest {

  @Test
  void test1() {
    assertThat(gIdxLeftGIndex(4)).isEqualTo(8);
    assertThat(gIdxRightGIndex(4)).isEqualTo(9);
  }

}
