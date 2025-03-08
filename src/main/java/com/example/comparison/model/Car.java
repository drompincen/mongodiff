package com.example.comparison.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "carCollection")
public class Car {
    @Id
    private String vin;
    private String make;
    private String model;
    private int year;
    private String color;
    private long mileage;
    private String engineType;
    private String transmission;
    private double price;
    private String fuelType;
    private String owner;
    private Date registrationDate;
    private String registrationState;
    private Date lastServiceDate;
    private String warrantyStatus;

    // Getters and setters

    public String getVin() {
        return vin;
    }
    public void setVin(String vin) {
        this.vin = vin;
    }
    public String getMake() {
        return make;
    }
    public void setMake(String make) {
        this.make = make;
    }
    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public int getYear() {
        return year;
    }
    public void setYear(int year) {
        this.year = year;
    }
    public String getColor() {
        return color;
    }
    public void setColor(String color) {
        this.color = color;
    }
    public long getMileage() {
        return mileage;
    }
    public void setMileage(long mileage) {
        this.mileage = mileage;
    }
    public String getEngineType() {
        return engineType;
    }
    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }
    public String getTransmission() {
        return transmission;
    }
    public void setTransmission(String transmission) {
        this.transmission = transmission;
    }
    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public String getFuelType() {
        return fuelType;
    }
    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }
    public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }
    public Date getRegistrationDate() {
        return registrationDate;
    }
    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }
    public String getRegistrationState() {
        return registrationState;
    }
    public void setRegistrationState(String registrationState) {
        this.registrationState = registrationState;
    }
    public Date getLastServiceDate() {
        return lastServiceDate;
    }
    public void setLastServiceDate(Date lastServiceDate) {
        this.lastServiceDate = lastServiceDate;
    }
    public String getWarrantyStatus() {
        return warrantyStatus;
    }
    public void setWarrantyStatus(String warrantyStatus) {
        this.warrantyStatus = warrantyStatus;
    }
}
