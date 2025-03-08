package com.example.comparison;

import com.example.comparison.model.Account;
import com.example.comparison.model.ComparisonBreak;
import com.example.comparison.service.GenericComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataMongoTest
@Import(GenericComparisonService.class)
public class GenericComparisonServiceTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private GenericComparisonService genericComparisonService;

    private final List<String> attributesToCompare = List.of(
            "accountName", "accountType", "broker", "creationDate",
            "balance", "currency", "riskLevel", "lastTradeDate",
            "totalTrades", "availableMargin"
    );

    @BeforeEach
    public void setUp() {
        mongoTemplate.dropCollection("accountCollectionA");
        mongoTemplate.dropCollection("accountCollectionB");
        mongoTemplate.dropCollection("genericComparisonBreaks");

        List<Account> accountsA = new ArrayList<>();
        List<Account> accountsB = new ArrayList<>();

        // Use a fixed base date so that creationDate and lastTradeDate are deterministic.
        Date baseDate = new Date(1630000000000L);

        // Create 1000 matching accounts in both collections.
        for (int i = 1; i <= 1000; i++) {
            String accountId = String.format("acct%04d", i);
            Account accountA = generateAccount(accountId, i, baseDate);
            Account accountB = generateAccount(accountId, i, baseDate);
            // Plant a difference in 100 accounts (modify balance in B)
            if (i <= 100) {
                accountB.setBalance(accountB.getBalance() + 10.0);
            }
            accountsA.add(accountA);
            accountsB.add(accountB);
        }

        // Add 5 extra accounts to collection A (missing in B)
        for (int i = 1001; i <= 1005; i++) {
            String accountId = String.format("acct%04d", i);
            accountsA.add(generateAccount(accountId, i, baseDate));
        }

        // Add 10 extra accounts to collection B (missing in A)
        for (int i = 2001; i <= 2010; i++) {
            String accountId = String.format("acct%04d", i);
            accountsB.add(generateAccount(accountId, i, baseDate));
        }

        mongoTemplate.insert(accountsA, "accountCollectionA");
        mongoTemplate.insert(accountsB, "accountCollectionB");
    }

    private Account generateAccount(String accountId, int index, Date baseDate) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setAccountName("Account " + accountId);
        account.setAccountType(index % 2 == 0 ? "Margin" : "Cash");
        account.setBroker("BrokerX");

        // Deterministic dates based on a fixed baseDate.
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

    @Test
    public void testGenericCompareAccounts() {
        genericComparisonService.compareCollections(
                Account.class,
                "accountCollectionA",
                "accountCollectionB",
                "accountId",
                attributesToCompare,
                "genericComparisonBreaks"
        );

        List<ComparisonBreak> breaks = mongoTemplate.findAll(ComparisonBreak.class, "genericComparisonBreaks");

        // Expected differences:
        //  - 100 accounts with a planted balance difference ("difference")
        //  - 5 accounts present only in A ("onlyOnA")
        //  - 10 accounts present only in B ("onlyOnB")
        int expectedDifferences = 100 + 5 + 10; // 115 total breaks
        assertEquals(expectedDifferences, breaks.size(),
                "Expected " + expectedDifferences + " differences, but found " + breaks.size());

        // Optionally, verify break types
        long differenceCount = breaks.stream().filter(b -> "difference".equals(b.getBreakType())).count();
        long onlyOnACount = breaks.stream().filter(b -> "onlyOnA".equals(b.getBreakType())).count();
        long onlyOnBCount = breaks.stream().filter(b -> "onlyOnB".equals(b.getBreakType())).count();

        assertEquals(100, differenceCount, "Expected 100 'difference' breaks");
        assertEquals(5, onlyOnACount, "Expected 5 'onlyOnA' breaks");
        assertEquals(10, onlyOnBCount, "Expected 10 'onlyOnB' breaks");
    }
}
