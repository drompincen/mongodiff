package infra.fingerprint;


import java.util.*;
import java.time.LocalDate;
import java.util.stream.Collectors;

public class TestVehicleFingerprinter {
    public static void main(String[] args) {
        Random rnd = new Random();
        List<Vehicle> vehicles = new ArrayList<>(1_000);
        Map<String,String> idToType = new HashMap<>(1_000);

        String[] makes  = {"Ford","Toyota","Honda","Chevrolet"};
        String[] models = {"F-150","Camry","Civic","Silverado"};
        String[] colors = {"Red","Blue","Black","White"};
        int currentYear = LocalDate.now().getYear();

        // generate 1,000 vehicles, ~95% CAR, ~5% VAN
        for (int i = 0; i < 1_000; i++) {
            Vehicle v = new Vehicle();
            String id = UUID.randomUUID().toString();
            v.setVehicleId(id);

            v.setMake(makes[rnd.nextInt(makes.length)]);
            v.setModel(models[rnd.nextInt(models.length)]);
            v.setYear(currentYear - rnd.nextInt(15));
            v.setColor(colors[rnd.nextInt(colors.length)]);
            // 5% vans
            String type = rnd.nextDouble() < 0.05 ? "VAN" : "CAR";
            v.setType(type);

            v.setMileage(Math.round(rnd.nextDouble() * 200_000));
            v.setVin("VIN" + rnd.nextInt(1_000_000));
            v.setOwner("Owner" + rnd.nextInt(500));
            v.setLicensePlate("LP" + rnd.nextInt(100_000));

            vehicles.add(v);
            idToType.put(id, type);
        }

        // fingerprint by 'type' only, sample up to 200 IDs per group
        Fingerprinter<Vehicle, String> fp = new Fingerprinter<>(
                Vehicle.class,
                Collections.singletonList("type"),
                "vehicleId",
                200
        );
        fp.process(vehicles);

        // show raw group summaries:
        System.out.println("=== Group Summaries ===");
        fp.getGroupSummaries().forEach(s -> {
            System.out.printf("Type=%s | count=%d | sampledIds=%d%n",
                    s.getFields().get("type"),
                    s.getCount(),
                    s.getSampleIds().size());
        });

        // now pull a diverse sample of 100 IDs:
        List<String> diverse = fp.getDiverseSample(100);
        System.out.println("\n=== Diverse-sample distribution ===");
        Map<String, Long> dist = diverse.stream()
                .map(idToType::get)
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        dist.forEach((type, cnt) ->
                System.out.printf("%s → %d%n", type, cnt)
        );
    }
}