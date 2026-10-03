/**
 * Pure Java implementation of the Builder pattern.
 *
 * Demonstrates how to construct an immutable object
 * with multiple optional parameters without telescoping constructors.
 */
public class BuilderExample {

  // ===== Usage / Demo =====
  public static void main(String[] args) {
    House house = House.builder()
      .walls(4)
      .roof("tiles")
      .hasGarage(true)
      .floorCount(2)
      .build();

    System.out.println(house);

    if (house.getWalls() != 4) {
      throw new IllegalStateException("Invalid number of walls");
    }
  }

  // ===== Product =====
  static class House {

    private final int walls;
    private final String roof;
    private final boolean hasGarage;
    private final int floorCount;

    private House(Builder builder) {
      this.walls = builder.walls;
      this.roof = builder.roof;
      this.hasGarage = builder.hasGarage;
      this.floorCount = builder.floorCount;
    }

    public static Builder builder() {
      return new Builder();
    }

    public int getWalls() {
      return walls;
    }

    public String getRoof() {
      return roof;
    }

    public boolean hasGarage() {
      return hasGarage;
    }

    public int getFloorCount() {
      return floorCount;
    }

    @Override
    public String toString() {
      return "House{" +
        "walls=" + walls +
        ", roof='" + roof + '\'' +
        ", hasGarage=" + hasGarage +
        ", floorCount=" + floorCount +
        '}';
    }
  }

  // ===== Builder =====
  static class Builder {

    private int walls;
    private String roof;
    private boolean hasGarage;
    private int floorCount;

    public Builder walls(int walls) {
      this.walls = walls;
      return this;
    }

    public Builder roof(String roof) {
      this.roof = roof;
      return this;
    }

    public Builder hasGarage(boolean hasGarage) {
      this.hasGarage = hasGarage;
      return this;
    }

    public Builder floorCount(int floorCount) {
      this.floorCount = floorCount;
      return this;
    }

    public House build() {
      validate();
      return new House(this);
    }

    private void validate() {
      if (walls <= 0) {
        throw new IllegalStateException("House must have walls");
      }
      if (roof == null || roof.isBlank()) {
        throw new IllegalStateException("Roof must be specified");
      }
    }
  }
}