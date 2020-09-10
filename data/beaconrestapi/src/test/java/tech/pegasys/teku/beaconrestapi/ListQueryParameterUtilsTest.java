package tech.pegasys.teku.beaconrestapi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ListQueryParameterUtilsTest {
  @Test
  public void integerList_shouldHandleMultipleIndividualEntries() {
    final Map data = Map.of("index", List.of("1", "2","3"));
    assertThat(ListQueryParameterUtils.getParameterAsIntegerList(data, "index")).isEqualTo(List.of(1,2,3));
  }
}
