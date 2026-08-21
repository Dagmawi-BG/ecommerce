package com.ecommerce.store.model;

/**
 * Structured shipping snapshot stored as JSONB on the order. A plain class
 * (no-arg ctor + accessors) keeps Hibernate's JSON (de)serialization simple.
 */
public class ShippingAddress {

    private String street;
    private String city;
    private String zip;

    public ShippingAddress() {
    }

    public ShippingAddress(String street, String city, String zip) {
        this.street = street;
        this.city = city;
        this.zip = zip;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }
}
