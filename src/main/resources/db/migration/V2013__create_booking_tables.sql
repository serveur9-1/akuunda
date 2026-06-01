-- ====================================================================
-- Migration: Créer les tables pour le système de réservation
-- Date: 2025-04-10
-- Description: Tables pour hôtels, chambres, transport, véhicules et réservations
-- ====================================================================

-- Table des hôtels
CREATE TABLE IF NOT EXISTS hotels (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(2000),
    address           VARCHAR(500) NOT NULL,
    city              VARCHAR(100) NOT NULL,
    country           VARCHAR(100) NOT NULL,
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    image_url         VARCHAR(500),
    images            VARCHAR(2000),
    rating            DOUBLE PRECISION DEFAULT 0,
    review_count      INTEGER DEFAULT 0,
    amenities         VARCHAR(1000),
    phone             VARCHAR(50),
    email             VARCHAR(255),
    is_verified       BOOLEAN DEFAULT FALSE,
    is_active         BOOLEAN DEFAULT TRUE,
    owner_id          VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hotel_city ON hotels(city);
CREATE INDEX IF NOT EXISTS idx_hotel_country ON hotels(country);
CREATE INDEX IF NOT EXISTS idx_hotel_owner ON hotels(owner_id);
CREATE INDEX IF NOT EXISTS idx_hotel_is_active ON hotels(is_active);

-- Table des chambres
CREATE TABLE IF NOT EXISTS rooms (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(1000),
    price_per_night   DOUBLE PRECISION NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'EUR',
    max_guests        INTEGER NOT NULL,
    amenities         VARCHAR(1000),
    images            VARCHAR(2000),
    is_available      BOOLEAN DEFAULT TRUE,
    total_rooms       INTEGER DEFAULT 1,
    hotel_id          BIGINT NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_room_hotel ON rooms(hotel_id);
CREATE INDEX IF NOT EXISTS idx_room_is_available ON rooms(is_available);

-- Table des prestataires de transport
CREATE TABLE IF NOT EXISTS transport_providers (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(1000),
    photo_url         VARCHAR(500),
    phone             VARCHAR(50),
    email             VARCHAR(255),
    city              VARCHAR(100),
    country           VARCHAR(100),
    rating            DOUBLE PRECISION DEFAULT 0,
    review_count      INTEGER DEFAULT 0,
    completed_trips   INTEGER DEFAULT 0,
    service_areas     VARCHAR(1000),
    service_types     VARCHAR(500),
    is_verified       BOOLEAN DEFAULT FALSE,
    is_available      BOOLEAN DEFAULT TRUE,
    owner_id          VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transport_provider_owner ON transport_providers(owner_id);
CREATE INDEX IF NOT EXISTS idx_transport_provider_city ON transport_providers(city);
CREATE INDEX IF NOT EXISTS idx_transport_provider_is_available ON transport_providers(is_available);

-- Table des véhicules
CREATE TABLE IF NOT EXISTS vehicles (
    id                    BIGSERIAL PRIMARY KEY,
    type                  VARCHAR(50) NOT NULL,
    brand                 VARCHAR(100) NOT NULL,
    model                 VARCHAR(100) NOT NULL,
    plate_number          VARCHAR(50) NOT NULL,
    max_passengers        INTEGER NOT NULL,
    max_luggage           INTEGER DEFAULT 2,
    price_per_km          DOUBLE PRECISION NOT NULL,
    base_price            DOUBLE PRECISION NOT NULL,
    currency              VARCHAR(10) NOT NULL DEFAULT 'EUR',
    images                VARCHAR(2000),
    has_air_conditioning  BOOLEAN DEFAULT TRUE,
    has_wifi              BOOLEAN DEFAULT FALSE,
    is_available          BOOLEAN DEFAULT TRUE,
    provider_id           BIGINT NOT NULL REFERENCES transport_providers(id) ON DELETE CASCADE,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_vehicle_provider ON vehicles(provider_id);
CREATE INDEX IF NOT EXISTS idx_vehicle_type ON vehicles(type);

-- Table des réservations
CREATE TABLE IF NOT EXISTS bookings (
    id                        BIGSERIAL PRIMARY KEY,
    reference                 VARCHAR(50) NOT NULL UNIQUE,
    type                      VARCHAR(20) NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    
    -- Utilisateur
    user_id                   VARCHAR(255) NOT NULL,
    user_name                 VARCHAR(255),
    user_phone                VARCHAR(50),
    user_email                VARCHAR(255),
    
    -- Prestataire
    provider_user_id          VARCHAR(255) NOT NULL,
    provider_name             VARCHAR(255),
    
    -- Montants
    amount                    DOUBLE PRECISION NOT NULL,
    service_fee               DOUBLE PRECISION DEFAULT 0,
    total_amount              DOUBLE PRECISION NOT NULL,
    currency                  VARCHAR(10) NOT NULL DEFAULT 'EUR',
    
    -- QR Code & Escrow
    qr_code                   VARCHAR(500),
    qr_code_url               VARCHAR(500),
    conditional_payment_id    BIGINT,
    conditional_payment_code  VARCHAR(50),
    funds_locked              BOOLEAN DEFAULT FALSE,
    funds_locked_at           TIMESTAMP,
    funds_released_at         TIMESTAMP,
    
    -- Dates
    created_at                TIMESTAMP NOT NULL,
    accepted_at               TIMESTAMP,
    rejected_at               TIMESTAMP,
    cancelled_at              TIMESTAMP,
    completed_at              TIMESTAMP,
    expires_at                TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP NOT NULL,
    
    -- Détails Hôtel
    hotel_id                  BIGINT REFERENCES hotels(id),
    room_id                   BIGINT REFERENCES rooms(id),
    check_in_date             TIMESTAMP,
    check_out_date            TIMESTAMP,
    number_of_nights          INTEGER,
    number_of_guests          INTEGER,
    number_of_rooms           INTEGER DEFAULT 1,
    
    -- Détails Transport
    transport_provider_id     BIGINT REFERENCES transport_providers(id),
    vehicle_id                BIGINT REFERENCES vehicles(id),
    transport_type            VARCHAR(20),
    pickup_address            VARCHAR(500),
    pickup_latitude           DOUBLE PRECISION,
    pickup_longitude          DOUBLE PRECISION,
    dropoff_address           VARCHAR(500),
    dropoff_latitude          DOUBLE PRECISION,
    dropoff_longitude         DOUBLE PRECISION,
    pickup_date_time          TIMESTAMP,
    flight_number             VARCHAR(20),
    number_of_passengers      INTEGER,
    number_of_luggage         INTEGER,
    estimated_distance        DOUBLE PRECISION,
    estimated_duration        INTEGER,
    
    -- Notes
    user_note                 VARCHAR(1000),
    provider_note             VARCHAR(1000),
    cancellation_reason       VARCHAR(500),
    rejection_reason          VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_booking_reference ON bookings(reference);
CREATE INDEX IF NOT EXISTS idx_booking_user ON bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_booking_provider_user ON bookings(provider_user_id);
CREATE INDEX IF NOT EXISTS idx_booking_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_booking_type ON bookings(type);
CREATE INDEX IF NOT EXISTS idx_booking_conditional_payment ON bookings(conditional_payment_id);
