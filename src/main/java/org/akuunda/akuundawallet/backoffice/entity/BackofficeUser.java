package org.akuunda.akuundawallet.backoffice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backoffice_user")
public class BackofficeUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(nullable = false, length = 16)
    private String portal;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "wallet_username")
    private String walletUsername;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "address")
    private String address;

    @Column(name = "contact_phone")
    private String contactPhone;

    public BackofficeUser() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final BackofficeUser u = new BackofficeUser();
        public Builder id(UUID id) { u.id = id; return this; }
        public Builder email(String email) { u.email = email; return this; }
        public Builder passwordHash(String passwordHash) { u.passwordHash = passwordHash; return this; }
        public Builder firstName(String firstName) { u.firstName = firstName; return this; }
        public Builder lastName(String lastName) { u.lastName = lastName; return this; }
        public Builder portal(String portal) { u.portal = portal; return this; }
        public Builder enabled(boolean enabled) { u.enabled = enabled; return this; }
        public Builder createdAt(java.time.Instant createdAt) { u.createdAt = createdAt; return this; }
        public Builder walletUsername(String walletUsername) { u.walletUsername = walletUsername; return this; }
        public Builder businessName(String businessName) { u.businessName = businessName; return this; }
        public Builder registrationNumber(String registrationNumber) { u.registrationNumber = registrationNumber; return this; }
        public Builder businessType(String businessType) { u.businessType = businessType; return this; }
        public Builder address(String address) { u.address = address; return this; }
        public Builder contactPhone(String contactPhone) { u.contactPhone = contactPhone; return this; }
        public BackofficeUser build() { return u; }
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPortal() { return portal; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public String getWalletUsername() { return walletUsername; }
    public String getBusinessName() { return businessName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getBusinessType() { return businessType; }
    public String getAddress() { return address; }
    public String getContactPhone() { return contactPhone; }

    public void setId(UUID id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setPortal(String portal) { this.portal = portal; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setWalletUsername(String walletUsername) { this.walletUsername = walletUsername; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public void setAddress(String address) { this.address = address; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
