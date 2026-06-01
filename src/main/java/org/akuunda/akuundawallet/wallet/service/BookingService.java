package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.booking.*;
import org.akuunda.akuundawallet.wallet.api.enums.BookingStatus;
import org.akuunda.akuundawallet.wallet.api.enums.BookingType;
import org.akuunda.akuundawallet.wallet.api.enums.TransportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface BookingService {

    // ── Hôtels ───────────────────────────────────────────────────────────────
    ResponseEntity<Page<HotelResponse>> getHotels(String city, String country, String search, Pageable pageable);
    ResponseEntity<HotelResponse> getHotelById(Long hotelId);
    ResponseEntity<Page<RoomResponse>> getAvailableRooms(Long hotelId, Integer guests, Pageable pageable);

    // ── Transport ────────────────────────────────────────────────────────────
    ResponseEntity<Page<TransportProviderResponse>> getTransportProviders(String city, TransportType type, Pageable pageable);
    ResponseEntity<TransportProviderResponse> getTransportProviderById(Long providerId);

    // ── Réservations Client ──────────────────────────────────────────────────
    ResponseEntity<BookingResponse> createHotelBooking(String clientUsername, CreateHotelBookingRequest request);
    ResponseEntity<BookingResponse> createTransportBooking(String clientUsername, CreateTransportBookingRequest request);
    ResponseEntity<Page<BookingResponse>> getMyBookings(String username, BookingStatus status, BookingType type, Pageable pageable);
    ResponseEntity<BookingResponse> getBookingById(Long bookingId);
    ResponseEntity<BookingResponse> getBookingByReference(String reference);
    ResponseEntity<BookingResponse> cancelBooking(String bookingReference, String username, CancelBookingRequest request);

    // ── Réservations Prestataire ─────────────────────────────────────────────
    ResponseEntity<Page<BookingResponse>> getProviderBookings(String providerUsername, BookingStatus status, Pageable pageable);
    ResponseEntity<BookingResponse> acceptBooking(String bookingReference, String providerUsername, AcceptBookingRequest request);
    ResponseEntity<BookingResponse> rejectBooking(String bookingReference, String providerUsername, RejectBookingRequest request);
    ResponseEntity<BookingResponse> completeBookingByQrCode(String qrCode, String providerUsername);
}
