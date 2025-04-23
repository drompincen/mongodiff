package infra.fingerprint;

import java.time.LocalDate;
import java.util.*;

public class TestFingerPrint {
    public static void main(String[] args) {
        // 1) Generate 10,000 random Account objects
        List<Account> accounts = new ArrayList<>(10_000);
        Random rnd = new Random();
        String[] states       = {"NY","CA","TX","FL","IL","PA","OH","GA","NC","MI"};
        String[] cities       = {"New York","Los Angeles","Chicago","Houston","Phoenix"};
        String[] acctTypes    = {"CHECKING","SAVINGS","LOAN"};
        String[] statuses     = {"ACTIVE","INACTIVE","CLOSED"};
        String[] productTypes = {"PERSONAL","BUSINESS","PREMIER"};

        for (int i = 0; i < 10_000; i++) {
            Account a = new Account();
            a.setAccountId(UUID.randomUUID().toString());
            a.setFirstName("First" + rnd.nextInt(1_000));
            a.setLastName("Last"  + rnd.nextInt(1_000));
            a.setEmail("user" + i + "@example.com");
            a.setBalance(Math.round(rnd.nextDouble() * 1_000_000_00) / 100.0);
            a.setAccountType(acctTypes[rnd.nextInt(acctTypes.length)]);
            a.setStatus(statuses[rnd.nextInt(statuses.length)]);
            a.setCreatedDate(LocalDate.now().minusDays(rnd.nextInt(3_650)));
            String state = states[rnd.nextInt(states.length)];
            a.setState(state);
            a.setCity(cities[rnd.nextInt(cities.length)]);
            a.setZip(String.format("%05d", rnd.nextInt(100_000)));
            a.setProductType(productTypes[rnd.nextInt(productTypes.length)]);
            accounts.add(a);
        }

        // 2) Fingerprint by state, city, zip; sample up to 5 IDs per group
        Fingerprinter<Account, String> fp = new Fingerprinter<>(
                Account.class,
                Arrays.asList("state", "city", "zip"),
                "accountId",
                5
        );
        fp.process(accounts);
        List<Map.Entry<Fingerprinter.GroupKey, Fingerprinter.GroupStats<String>>> top100 =
                fp.topGroups(100);
        // print them
        top100.forEach(e -> {
            System.out.printf("%s → count=%d, sampleIds=%s%n",
                    e.getKey(),
                    e.getValue().getCount(),
                    e.getValue().getSampleIds());
        });

    }
}
