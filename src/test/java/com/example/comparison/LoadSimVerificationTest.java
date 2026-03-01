package com.example.comparison;

import com.example.comparison.model.Car;
import com.example.comparison.service.GenericComparisonService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Load test that connects to a real local MongoDB instance.
 * Validates the merge-join comparison at scale (400k records) against
 * a real MongoDB sort to catch any collation/alignment issues.
 *
 * Requires: MongoDB running on localhost:27017
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoadSimVerificationTest {

    private static final Logger logger = LoggerFactory.getLogger(LoadSimVerificationTest.class);

    private static final int TOTAL_CARS = 400_000;
    private static final int BATCH_SIZE = 10_000;

    // Deletion counts
    private static final int DELETE_FIRST = 50_000;
    private static final int DELETE_AT_60_PCT = 10_000;
    private static final int DELETE_LAST = 5_000;
    private static final int TOTAL_DELETED = DELETE_FIRST + DELETE_AT_60_PCT + DELETE_LAST; // 65,000
    private static final int REMAINING = TOTAL_CARS - TOTAL_DELETED; // 335,000

    private static final int COLOR_MODIFICATIONS = 100;

    private static final String CARS_A = "carsA";
    private static final String CARS_B = "carsB";
    private static final String BREAKS = "loadSimBreaks";
    private static final List<String> COMPARE_ATTRS = List.of("color", "engineType", "make", "model");

    private MongoClient mongoClient;
    private MongoTemplate mongoTemplate;
    private GenericComparisonService service;

    @BeforeAll
    void setUp() {
        mongoClient = MongoClients.create("mongodb://localhost:27017/?connectTimeoutMS=3000&serverSelectionTimeoutMS=3000");
        try {
            mongoClient.getDatabase("admin").runCommand(new org.bson.Document("ping", 1));
        } catch (Exception e) {
            logger.info("Skipping LoadSimVerificationTest: local MongoDB not available ({})", e.getMessage());
            mongoClient.close();
            mongoClient = null;
            assumeTrue(false, "Local MongoDB on localhost:27017 is required for this test");
        }
        mongoTemplate = new MongoTemplate(mongoClient, "LoadSimVerification");
        service = new GenericComparisonService();
        service.setMongoTemplate(mongoTemplate);
    }

    @AfterAll
    void tearDown() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Test
    @DisplayName("400k cars: delete 65k from B, modify 100 colors, compare and verify alignment")
    void testLargeCollectionComparison() {
        // Clean slate
        mongoTemplate.dropCollection(CARS_A);
        mongoTemplate.dropCollection(CARS_B);
        mongoTemplate.dropCollection(BREAKS);

        // --- Step 1: Load 400k cars into both carsA and carsB ---
        logger.info("Loading {} cars into '{}' and '{}'...", TOTAL_CARS, CARS_A, CARS_B);
        long loadStart = System.currentTimeMillis();
        for (int batchStart = 1; batchStart <= TOTAL_CARS; batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE - 1, TOTAL_CARS);
            List<Car> batch = new ArrayList<>(BATCH_SIZE);
            for (int i = batchStart; i <= batchEnd; i++) {
                batch.add(createCar(i));
            }
            mongoTemplate.insert(batch, CARS_A);
            mongoTemplate.insert(batch, CARS_B);
            if (batchEnd % 100_000 == 0 || batchEnd == TOTAL_CARS) {
                logger.info("  Loaded {}/{}", batchEnd, TOTAL_CARS);
            }
        }
        logger.info("Loading complete in {} ms.", System.currentTimeMillis() - loadStart);

        assertEquals(TOTAL_CARS, mongoTemplate.count(new Query(), CARS_A), "carsA should have all records");
        assertEquals(TOTAL_CARS, mongoTemplate.count(new Query(), CARS_B), "carsB should have all records");

        // --- Step 2: Delete from carsB ---
        logger.info("Deleting {} records from '{}'...", TOTAL_DELETED, CARS_B);

        // First 50k: VIN000001 through VIN050000
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").lte(vin(DELETE_FIRST))),
                CARS_B);

        // 10k from 60% mark: VIN240001 through VIN250000
        int mark60Start = (int) (TOTAL_CARS * 0.6) + 1; // 240001
        int mark60End = mark60Start + DELETE_AT_60_PCT - 1; // 250000
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").gte(vin(mark60Start)).lte(vin(mark60End))),
                CARS_B);

        // Last 5k: VIN395001 through VIN400000
        int lastStart = TOTAL_CARS - DELETE_LAST + 1; // 395001
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").gte(vin(lastStart))),
                CARS_B);

        long countB = mongoTemplate.count(new Query(), CARS_B);
        assertEquals(REMAINING, countB, "carsB count after deletions");
        logger.info("'{}' has {} records after deletions.", CARS_B, countB);

        // --- Step 3: Modify 100 cars' color in carsB ---
        // Pick VIN100001 through VIN100100 (safely within the remaining range 50001–240000)
        Set<String> modifiedVins = new LinkedHashSet<>();
        for (int i = 100_001; i <= 100_000 + COLOR_MODIFICATIONS; i++) {
            String v = vin(i);
            modifiedVins.add(v);
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(v)),
                    new Update().set("color", "MODIFIED_COLOR"),
                    CARS_B);
        }
        assertEquals(COLOR_MODIFICATIONS, modifiedVins.size());
        logger.info("Modified color for {} cars in '{}'.", modifiedVins.size(), CARS_B);

        // --- Step 4: Run comparison ---
        logger.info("Running comparison: '{}' vs '{}' (key=vin, attrs={})...", CARS_A, CARS_B, COMPARE_ATTRS);
        long compStart = System.currentTimeMillis();

        service.compareCollections(Car.class, CARS_A, CARS_B, "vin", COMPARE_ATTRS, BREAKS);

        long compDuration = System.currentTimeMillis() - compStart;
        logger.info("Comparison completed in {} ms.", compDuration);

        // --- Step 5: Assert results ---
        long onlyOnA = mongoTemplate.count(
                Query.query(Criteria.where("breakType").is("onlyOnA")), BREAKS);
        long onlyOnB = mongoTemplate.count(
                Query.query(Criteria.where("breakType").is("onlyOnB")), BREAKS);
        long differences = mongoTemplate.count(
                Query.query(Criteria.where("breakType").is("difference")), BREAKS);
        long matches = mongoTemplate.count(
                Query.query(Criteria.where("breakType").is("match")), BREAKS);
        long colorDiffs = mongoTemplate.count(
                Query.query(Criteria.where("breakType").is("difference")
                        .and("differenceField").is("color")), BREAKS);
        long total = mongoTemplate.count(new Query(), BREAKS);

        logger.info("Results: onlyOnA={}, onlyOnB={}, differences={} (color={}), matches={}, total={}",
                onlyOnA, onlyOnB, differences, colorDiffs, matches, total);

        // 65,000 records deleted from B → should appear as onlyOnA
        assertEquals(TOTAL_DELETED, onlyOnA,
                "Keys only in A should equal total deleted from B");

        // Nothing was added to B that isn't in A
        assertEquals(0, onlyOnB,
                "No keys should be only in B");

        // 100 color changes → 100 difference breaks (one per car, only color differs)
        assertEquals(COLOR_MODIFICATIONS, differences,
                "Attribute differences should equal number of color modifications");

        // All differences should be on the 'color' field only
        assertEquals(COLOR_MODIFICATIONS, colorDiffs,
                "All differences should be on the 'color' field");

        // Remaining minus color-modified = fully matched
        assertEquals(REMAINING - COLOR_MODIFICATIONS, matches,
                "Fully matched keys");

        // Total break records = onlyOnA + differences + matches (onlyOnB is 0)
        assertEquals(onlyOnA + differences + matches, total,
                "Total break records should sum correctly");
    }

    private Car createCar(int index) {
        Car car = new Car();
        car.setVin(vin(index));
        car.setMake("Make" + (index % 10));
        car.setModel("Model" + (index % 20));
        car.setYear(2015 + (index % 10));
        car.setColor("Color" + (index % 8));
        car.setEngineType("Type" + (index % 4));
        car.setMileage(index * 50L);
        car.setTransmission(index % 2 == 0 ? "Automatic" : "Manual");
        car.setPrice(15000.0 + (index % 1000));
        car.setFuelType(index % 3 == 0 ? "Electric" : (index % 2 == 0 ? "Gasoline" : "Diesel"));
        return car;
    }

    private static String vin(int index) {
        return String.format("VIN%06d", index);
    }
}
