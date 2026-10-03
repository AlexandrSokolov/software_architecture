import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Demonstrates the Builder pattern using Lombok.
 *
 * This example assumes Lombok annotation processing
 * is enabled in the IDE.
 */
public class LombokBuilderExample {

  @Getter
  @ToString
  @Builder
  static class House {

    // Required fields
    private final int walls;
    private final String roof;

    // Optional fields
    private final boolean hasGarage;
    private final int floorCount;
  }
}