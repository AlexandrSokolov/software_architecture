import org.junit.jupiter.api.Test;

public class LombokBuilderTest {
  @Test
  public void test() {
    LombokBuilderExample.House house = LombokBuilderExample.House.builder()
      .walls(4)
      .roof("tiles")
      .hasGarage(true)
      .floorCount(2)
      .build();

    System.out.println(house);

    // Simple sanity check
    if (house.getWalls() != 4) {
      throw new IllegalStateException("Invalid house configuration");
    }
  }
}
