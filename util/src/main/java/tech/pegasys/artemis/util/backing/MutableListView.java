package tech.pegasys.artemis.util.backing;

public interface MutableListView<C extends View> extends ImmutableListView<C> {

  void set(int index, C value);
}
