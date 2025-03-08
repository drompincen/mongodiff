package com.example.comparison;

import com.example.comparison.model.Car;
import com.example.comparison.model.ComparisonBreak;
import com.example.comparison.service.GenericComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataMongoTest
@Import(GenericComparisonService.class)
public class GenericComparisonServiceCarTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private GenericComparisonService genericComparisonService;

    // Define the list of car fields to compare (all non-key fields)
    private final List<String> carAttributesToCompare = List.of(
            "make", "model", "year", "color", "mileage",
            "engineType", "transmission", "price", "fuelType",
            "owner", "registrationDate", "registrationState",
            "lastServiceDate", "warrantyStatus"
    );

    // Candidate fields to modify when planting differences (a subset of carAttributesToCompare)
    private final List<String> candidateFields = List.of(
            "color", "mileage", "price", "fuelType", "owner", "warrantyStatus"
    );

    // Use a fixed random seed for determinism.
    private Random random = new Random(12345L);

    // This will accumulate the actual (effective) differences inserted in the overlapping records.
    private int overlappingFieldDifferences = 0;
    private final int extraInBCount = 3000; // extra records only in collection B

    @BeforeEach
    public void setUp() {
        mongoTemplate.dropCollection("carCollectionA");
        mongoTemplate.dropCollection("carCollectionB");
        mongoTemplate.dropCollection("genericCarComparisonBreaks");

        List<Car> carsA = new ArrayList<>();
        List<Car> carsB = new ArrayList<>();

        // Use a fixed base date for determinism.
        Date baseDate = new Date(1630000000000L);
        long oneDayMillis = 24L * 60 * 60 * 1000;

        // Generate 10,000 overlapping cars.
        for (int i = 1; i <= 10000; i++) {
            String vin = String.format("VIN%05d", i);
            Car carA = generateCar(vin, i, baseDate, oneDayMillis);
            Car carB = generateCar(vin, i, baseDate, oneDayMillis);

            // For the first 5,000 overlapping records, plant differences in collection B.
            if (i <= 5000) {
                // Randomly decide how many fields (between 5 and 10) to modify for this record.
                int fieldsToModify = random.nextInt(6) + 5; // yields 5 to 10
                // Effective differences is limited by the candidateFields size.
                int effectiveDifferences = Math.min(fieldsToModify, candidateFields.size());
                overlappingFieldDifferences += effectiveDifferences;

                // Create a copy of candidateFields, then shuffle it deterministically.
                List<String> fieldsToChange = new ArrayList<>(candidateFields);
                java.util.Collections.shuffle(fieldsToChange, random);

                // Modify the first 'effectiveDifferences' fields.
                for (int j = 0; j < effectiveDifferences; j++) {
                    String field = fieldsToChange.get(j);
                    modifyCarField(carB, field);
                }
            }
            carsA.add(carA);
            carsB.add(carB);
        }

        // Add extra 3,000 cars only in collection B.
        for (int i = 10001; i <= 13000; i++) {
            String vin = String.format("VIN%05d", i);
            Car car = generateCar(vin, i, baseDate, oneDayMillis);
            carsB.add(car);
        }

        mongoTemplate.insert(carsA, "carCollectionA");
        mongoTemplate.insert(carsB, "carCollectionB");
    }

    /**
     * Generate a Car object with sample deterministic values.
     */
    private Car generateCar(String vin, int index, Date baseDate, long oneDayMillis) {
        Car car = new Car();
        car.setVin(vin);
        car.setMake("Make" + (index % 5));
        car.setModel("Model" + (index % 20));
        car.setYear(2000 + (index % 21));
        car.setColor("Color" + (index % 10));
        car.setMileage(index * 100L);
        car.setEngineType("Engine" + (index % 3));
        car.setTransmission(index % 2 == 0 ? "Automatic" : "Manual");
        car.setPrice(20000.0 + index);
        car.setFuelType(index % 2 == 0 ? "Gasoline" : "Diesel");
        car.setOwner("Owner" + (index % 100));
        // registrationDate: baseDate minus index days
        car.setRegistrationDate(new Date(baseDate.getTime() - (index * oneDayMillis)));
        car.setRegistrationState("State" + (index % 50));
        // lastServiceDate: registrationDate plus 30 days
        car.setLastServiceDate(new Date(car.getRegistrationDate().getTime() + (30 * oneDayMillis)));
        car.setWarrantyStatus(index % 2 == 0 ? "Active" : "Expired");
        return car;
    }

    /**
     * Modify a field in the Car object to simulate a difference.
     * For simplicity, we append "_DIFF" for String fields,
     * add 100 for numeric fields, and swap values for warrantyStatus.
     */
    private void modifyCarField(Car car, String fieldName) {
        switch (fieldName) {
            case "color":
                car.setColor(car.getColor() + "_DIFF");
                break;
            case "mileage":
                car.setMileage(car.getMileage() + 100);
                break;
            case "price":
                car.setPrice(car.getPrice() + 100.0);
                break;
            case "fuelType":
                car.setFuelType(car.getFuelType() + "_DIFF");
                break;
            case "owner":
                car.setOwner(car.getOwner() + "_DIFF");
                break;
            case "warrantyStatus":
                car.setWarrantyStatus(car.getWarrantyStatus().equals("Active") ? "Expired" : "Active");
                break;
            default:
                // For any other candidate field, do nothing.
                break;
        }
    }

    @Test
    public void testGenericCompareCars() {
        genericComparisonService.compareCollections(
                Car.class,
                "carCollectionA",
                "carCollectionB",
                "vin",
                carAttributesToCompare,
                "genericCarComparisonBreaks"
        );

        List<ComparisonBreak> breaks = mongoTemplate.findAll(ComparisonBreak.class, "genericCarComparisonBreaks");

        // Expected differences:
        // - Overlapping records: from the 10,000 common cars,
        //   the first 5,000 records have planted differences totaling 'overlappingFieldDifferences'
        // - Extra records in collection B: 3,000 differences (each missing record yields one break with breakType "onlyOnB")
        int expectedTotalDifferences = overlappingFieldDifferences + extraInBCount;
        assertEquals(expectedTotalDifferences, breaks.size(),
                "Expected " + expectedTotalDifferences + " differences, but found " + breaks.size());

        // Verify break types.
        long differenceCount = breaks.stream().filter(b -> "difference".equals(b.getBreakType())).count();
        long onlyOnBCount = breaks.stream().filter(b -> "onlyOnB".equals(b.getBreakType())).count();
        assertEquals(overlappingFieldDifferences, differenceCount,
                "Expected " + overlappingFieldDifferences + " 'difference' breaks");
        assertEquals(extraInBCount, onlyOnBCount,
                "Expected " + extraInBCount + " 'onlyOnB' breaks");
    }
}
