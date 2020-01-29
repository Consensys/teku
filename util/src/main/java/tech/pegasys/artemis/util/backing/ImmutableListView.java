package tech.pegasys.artemis.util.backing;

public interface ImmutableListView<C extends View> extends View {

  int maxSize();

  int size();

  C get(int index);
}
