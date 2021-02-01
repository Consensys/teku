package tech.pegasys.teku.ssz.sos;

import java.util.function.Supplier;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class SszField {
  private final int index;
  private final Supplier<ViewType<?>> viewType;

  public SszField(int index, ViewType<?> viewType) {
    this(index, () -> viewType);
  }

  public SszField(int index, Supplier<ViewType<?>> viewType) {
    this.index = index;
    this.viewType = viewType;
  }

  public int getIndex() {
    return index;
  }

  public Supplier<ViewType<?>> getViewType() {
    return viewType;
  }
}
