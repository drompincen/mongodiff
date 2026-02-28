package com.example.comparison.controller;

import com.example.comparison.model.Account;
import com.example.comparison.model.ComparisonBreak;
import com.example.comparison.service.GenericComparisonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sample")
@CrossOrigin
public class SampleDataController {

    private static final Logger log = LoggerFactory.getLogger(SampleDataController.class);

    private static final String BASELINE = "accountBaseline";
    private static final String RC = "accountRC";
    private static final String BREAKS = "sampleComparisonBreaks";
    private static final List<String> ATTRIBUTES = Arrays.asList(
            "accountName", "accountType", "broker", "creationDate",
            "balance", "currency", "riskLevel", "lastTradeDate",
            "totalTrades", "availableMargin"
    );

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @Autowired
    private GenericComparisonService comparisonService;

    private List<ComparisonBreak> memBreaks;
    private String lastMode; // "db" or "mem"

    @PostMapping("/load")
    public Map<String, Object> loadSample() {
        log.info("POST /api/sample/load - loading sample data from MongoDB");
        if (mongoTemplate == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MongoDB is not available. Use 'Load Mongo Mem' instead.");
        }
        long start = System.currentTimeMillis();

        mongoTemplate.dropCollection(BASELINE);
        mongoTemplate.dropCollection(RC);
        mongoTemplate.dropCollection(BREAKS);

        Date baseDate = new Date(1630000000000L);
        List<Account> baselineAccounts = new ArrayList<>();
        List<Account> rcAccounts = new ArrayList<>();

        // 200 matching accounts in both collections
        for (int i = 1; i <= 200; i++) {
            String accountId = String.format("acct%04d", i);
            Account a = generateAccount(accountId, i, baseDate);
            Account b = generateAccount(accountId, i, baseDate);
            // Plant balance difference in first 30
            if (i <= 30) {
                b.setBalance(b.getBalance() + 10.0);
            }
            baselineAccounts.add(a);
            rcAccounts.add(b);
        }

        // 5 extra only in baseline (acct0201-acct0205)
        for (int i = 201; i <= 205; i++) {
            String accountId = String.format("acct%04d", i);
            baselineAccounts.add(generateAccount(accountId, i, baseDate));
        }

        // 10 extra only in RC (acct0301-acct0310)
        for (int i = 301; i <= 310; i++) {
            String accountId = String.format("acct%04d", i);
            rcAccounts.add(generateAccount(accountId, i, baseDate));
        }

        mongoTemplate.insert(baselineAccounts, BASELINE);
        mongoTemplate.insert(rcAccounts, RC);

        comparisonService.compareCollections(
                Account.class, BASELINE, RC,
                "accountId", ATTRIBUTES, BREAKS
        );

        long elapsed = System.currentTimeMillis() - start;
        lastMode = "db";
        memBreaks = null;
        return buildResponseFromDb(elapsed);
    }

    @PostMapping("/load-mem")
    public Map<String, Object> loadSampleInMemory() {
        log.info("POST /api/sample/load-mem - loading sample data in-memory");
        long start = System.currentTimeMillis();

        Date baseDate = new Date(1630000000000L);
        List<Account> baselineAccounts = new ArrayList<>();
        List<Account> rcAccounts = new ArrayList<>();

        for (int i = 1; i <= 200; i++) {
            String accountId = String.format("acct%04d", i);
            Account a = generateAccount(accountId, i, baseDate);
            Account b = generateAccount(accountId, i, baseDate);
            if (i <= 30) {
                b.setBalance(b.getBalance() + 10.0);
            }
            baselineAccounts.add(a);
            rcAccounts.add(b);
        }

        for (int i = 201; i <= 205; i++) {
            String accountId = String.format("acct%04d", i);
            baselineAccounts.add(generateAccount(accountId, i, baseDate));
        }

        for (int i = 301; i <= 310; i++) {
            String accountId = String.format("acct%04d", i);
            rcAccounts.add(generateAccount(accountId, i, baseDate));
        }

        GenericComparisonService.ListComparisonResult<Account> result =
                comparisonService.compareLists(baselineAccounts, rcAccounts, "accountId", ATTRIBUTES);

        long elapsed = System.currentTimeMillis() - start;
        memBreaks = result.breaks;
        lastMode = "mem";
        return buildResponseFromBreaks(memBreaks, elapsed);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        log.info("GET /api/sample/status - lastMode={}", lastMode);
        if ("mem".equals(lastMode) && memBreaks != null) {
            return buildResponseFromBreaks(memBreaks, null);
        }
        if (mongoTemplate == null || !mongoTemplate.collectionExists(BREAKS)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("loaded", false);
            return empty;
        }
        return buildResponseFromDb(null);
    }

    @GetMapping("/breaks/{comparisonKey}")
    public List<ComparisonBreak> breaks(@PathVariable String comparisonKey) {
        log.info("GET /api/sample/breaks/{} - lastMode={}", comparisonKey, lastMode);
        if ("mem".equals(lastMode) && memBreaks != null) {
            return memBreaks.stream()
                    .filter(b -> comparisonKey.equals(b.getComparisonKey()))
                    .collect(Collectors.toList());
        }
        if (mongoTemplate == null) {
            return Collections.emptyList();
        }
        Query query = Query.query(Criteria.where("comparisonKey").is(comparisonKey));
        return mongoTemplate.find(query, ComparisonBreak.class, BREAKS);
    }

    private Map<String, Object> buildResponseFromDb(Long durationMs) {
        List<ComparisonBreak> all = mongoTemplate.findAll(ComparisonBreak.class, BREAKS);
        return buildResponseFromBreaks(all, durationMs);
    }

    private Map<String, Object> buildResponseFromBreaks(List<ComparisonBreak> all, Long durationMs) {
        Map<String, List<ComparisonBreak>> grouped = all.stream()
                .collect(Collectors.groupingBy(ComparisonBreak::getComparisonKey));

        int totalAttrs = ATTRIBUTES.size();
        String durationStr = durationMs != null ? String.format("%.1fs", durationMs / 1000.0) : null;

        List<Map<String, Object>> scope = new ArrayList<>();
        for (Map.Entry<String, List<ComparisonBreak>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<ComparisonBreak> keyBreaks = entry.getValue();

            boolean isMatch = keyBreaks.stream().anyMatch(b -> "match".equals(b.getBreakType()));
            boolean isOnlyOnA = keyBreaks.stream().anyMatch(b -> "onlyOnA".equals(b.getBreakType()));
            boolean isOnlyOnB = keyBreaks.stream().anyMatch(b -> "onlyOnB".equals(b.getBreakType()));
            long diffCount = keyBreaks.stream().filter(b -> "difference".equals(b.getBreakType())).count();

            int totalFields, matchedFields, breakFields;
            if (isOnlyOnA || isOnlyOnB) {
                totalFields = 1;
                matchedFields = 0;
                breakFields = 1;
            } else if (isMatch) {
                totalFields = totalAttrs;
                matchedFields = totalAttrs;
                breakFields = 0;
            } else {
                totalFields = totalAttrs;
                breakFields = (int) diffCount;
                matchedFields = totalAttrs - breakFields;
            }

            int matchPct = totalFields > 0 ? Math.round(matchedFields * 100f / totalFields) : 0;
            int breakPct = 100 - matchPct;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", key);
            item.put("name", "accountBaseline vs accountRC");
            item.put("category", "Sample Comparison");
            item.put("phase", "completed");
            item.put("matchPct", matchPct);
            item.put("breakPct", breakPct);
            item.put("totalFields", totalFields);
            item.put("matchedFields", matchedFields);
            item.put("breakFields", breakFields);
            item.put("duration", durationStr);
            scope.add(item);
        }

        scope.sort(Comparator.comparing(m -> (String) m.get("id")));

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("name", "Sample Comparison");
        session.put("startedAt", new Date().toInstant().toString());
        session.put("totalIds", scope.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("loaded", true);
        response.put("session", session);
        response.put("scope", scope);
        return response;
    }

    private Account generateAccount(String accountId, int index, Date baseDate) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setAccountName("Account " + accountId);
        account.setAccountType(index % 2 == 0 ? "Margin" : "Cash");
        account.setBroker("BrokerX");
        long oneDayMillis = 24L * 60 * 60 * 1000;
        Date creationDate = new Date(baseDate.getTime() - (index * oneDayMillis));
        account.setCreationDate(creationDate);
        account.setBalance(index * 1000.0);
        account.setCurrency("USD");
        account.setRiskLevel(index % 3 == 0 ? "High" : "Medium");
        Date lastTradeDate = new Date(creationDate.getTime() + oneDayMillis);
        account.setLastTradeDate(lastTradeDate);
        account.setTotalTrades(index % 100);
        account.setAvailableMargin(account.getBalance() * 0.1);
        account.setEmail(accountId + "@example.com");
        account.setPhoneNumber("555-010" + index);
        account.setAddress("123 Main St");
        account.setCountry("USA");
        account.setState("CA");
        account.setCity("Los Angeles");
        account.setZipCode("90001");
        account.setInvestmentStyle("Growth");
        account.setAccountStatus("Active");
        return account;
    }
}
