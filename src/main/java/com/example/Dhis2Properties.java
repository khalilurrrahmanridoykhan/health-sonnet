package com.example;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dhis2")
public class Dhis2Properties {

    private String baseUrl = "https://play.im.dhis2.org/stable-2-43-2-1";
    private String username = "admin";
    private String password = "district";
    private String defaultProgram = "IpHINAT79UW";
    private String defaultOrgUnit = "DiszpKrYNg8";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDefaultProgram() {
        return defaultProgram;
    }

    public void setDefaultProgram(String defaultProgram) {
        this.defaultProgram = defaultProgram;
    }

    public String getDefaultOrgUnit() {
        return defaultOrgUnit;
    }

    public void setDefaultOrgUnit(String defaultOrgUnit) {
        this.defaultOrgUnit = defaultOrgUnit;
    }
}
