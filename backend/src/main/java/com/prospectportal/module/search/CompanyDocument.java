package com.prospectportal.module.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

@Document(indexName = "#{@environment.getProperty('app.elasticsearch.index')}")
public class CompanyDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String cnpj;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String legalName;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String tradeName;

    @Field(type = FieldType.Keyword)
    private String cnaeMain;

    @Field(type = FieldType.Keyword)
    private String cnaeSecondary;

    @Field(type = FieldType.Text)
    private String cnaeDescription;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Keyword)
    private String state;

    @Field(type = FieldType.Keyword)
    private String neighborhood;

    @Field(type = FieldType.Text)
    private String street;

    @Field(type = FieldType.Keyword)
    private String zipCode;

    @Field(type = FieldType.Keyword)
    private String estimatedRevenue;

    @Field(type = FieldType.Keyword)
    private String dataSource;

    @Field(type = FieldType.Boolean)
    private boolean webContactable;

    @Field(type = FieldType.Keyword)
    private String registrationStatus;

    @Field(type = FieldType.Keyword)
    private String locationPrecision;

    @GeoPointField
    private GeoPoint location;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    public String getCnaeMain() {
        return cnaeMain;
    }

    public void setCnaeMain(String cnaeMain) {
        this.cnaeMain = cnaeMain;
    }

    public String getCnaeSecondary() {
        return cnaeSecondary;
    }

    public void setCnaeSecondary(String cnaeSecondary) {
        this.cnaeSecondary = cnaeSecondary;
    }

    public String getCnaeDescription() {
        return cnaeDescription;
    }

    public void setCnaeDescription(String cnaeDescription) {
        this.cnaeDescription = cnaeDescription;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getEstimatedRevenue() {
        return estimatedRevenue;
    }

    public void setEstimatedRevenue(String estimatedRevenue) {
        this.estimatedRevenue = estimatedRevenue;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isWebContactable() {
        return webContactable;
    }

    public void setWebContactable(boolean webContactable) {
        this.webContactable = webContactable;
    }

    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    public String getLocationPrecision() {
        return locationPrecision;
    }

    public void setLocationPrecision(String locationPrecision) {
        this.locationPrecision = locationPrecision;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }
}
