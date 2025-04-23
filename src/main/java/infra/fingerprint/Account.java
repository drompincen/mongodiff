package infra.fingerprint;

import java.time.LocalDate;

public class Account {
    private String accountId;
    private String firstName;
    private String lastName;
    private String email;
    private double balance;
    private String accountType;
    private String status;
    private LocalDate createdDate;
    private String state;
    private String city;
    private String zip;
    private String productType;

    // Getters & setters
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getZip() { return zip; }
    public void setZip(String zip) { this.zip = zip; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
}