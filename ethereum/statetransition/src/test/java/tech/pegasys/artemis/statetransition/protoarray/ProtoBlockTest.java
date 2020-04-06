package tech.pegasys.artemis.statetransition.protoarray;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtoBlockTest {

  @Test
  void listSplitTest() {
    List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));

    list.subList(0, 2).clear();

    assertThat(list.get(0)).isEqualTo(3);
    assertThat(list.size()).isEqualTo(4);
  }


}
