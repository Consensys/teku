package tech.pegasys.teku.ssz.backing;

import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;

public class SszTestUtils {

  public static List<Integer> getVectorLengths(ContainerViewType<?> containerViewType) {
    return containerViewType.getChildTypes().stream()
        .filter(t -> t instanceof VectorViewType)
        .map(t -> (VectorViewType<?>) t)
        .map(VectorViewType::getLength)
        .collect(Collectors.toList());
  }
}
