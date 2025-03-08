package com.example.comparison.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "accountCollection")
public class Account {
    @Id
    private String accountId;
    private String accountName;
    private String accountType;
    private String broker;
    private Date creationDate;
    private double balance;
    private String currency;
    private String riskLevel;
    private Date lastTradeDate;
    private int totalTrades;
    private double availableMargin;
    private String email;
    private String phoneNumber;
    private String address;
    private String country;
    private String state;
    private String city;
    private String zipCode;
    private String investmentStyle;
    private String accountStatus;

    // Getters and Setters

    public String getAccountId() {
        return accountId;
    }
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    public String getAccountName() {
        return accountName;
    }
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
    public String getAccountType() {
        return accountType;
    }
    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }
    public String getBroker() {
        return broker;
    }
    public void setBroker(String broker) {
        this.broker = broker;
    }
    public Date getCreationDate() {
        return creationDate;
    }
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }
    public double getBalance() {
        return balance;
    }
    public void setBalance(double balance) {
        this.balance = balance;
    }
    public String getCurrency() {
        return currency;
    }
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    public String getRiskLevel() {
        return riskLevel;
    }
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
    public Date getLastTradeDate() {
        return lastTradeDate;
    }
    public void setLastTradeDate(Date lastTradeDate) {
        this.lastTradeDate = lastTradeDate;
    }
    public int getTotalTrades() {
        return totalTrades;
    }
    public void setTotalTrades(int totalTrades) {
        this.totalTrades = totalTrades;
    }
    public double getAvailableMargin() {
        return availableMargin;
    }
    public void setAvailableMargin(double availableMargin) {
        this.availableMargin = availableMargin;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }
    public String getState() {
        return state;
    }
    public void setState(String state) {
        this.state = state;
    }
    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }
    public String getZipCode() {
        return zipCode;
    }
    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }
    public String getInvestmentStyle() {
        return investmentStyle;
    }
    public void setInvestmentStyle(String investmentStyle) {
        this.investmentStyle = investmentStyle;
    }
    public String getAccountStatus() {
        return accountStatus;
    }
    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }
}
