package tech.pegasys.teku.spec.executionengine.client.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class GenericResponse {

  private Boolean success;

  public GenericResponse(@JsonProperty("success") Boolean success) {
    this.success = success;
  }

  public Boolean getSuccess() {
    return success;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenericResponse that = (GenericResponse) o;
    return Objects.equals(success, that.success);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success);
  }

  @Override
  public String toString() {
    return "GenericResponse{" + "success=" + success + '}';
  }
}
