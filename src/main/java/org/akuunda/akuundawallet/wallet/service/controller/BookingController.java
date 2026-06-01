package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.booking.*;
import org.akuunda.akuundawallet.wallet.api.enums.BookingStatus;
import org.akuunda.akuundawallet.wallet.api.enums.BookingType;
import org.akuunda.akuundawallet.wallet.api.enums.TransportType;
import org.akuunda.akuundawallet.wallet.service.BookingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/internal/v1/booking")
@RequiredArgsConstructor
@Tag(name = "Booking", description = "API de reservation hotels et transport")
@CrossOrigin("*")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/hotels")
    @Operation(summary = "Lister les hotels", description = "Recupere la liste des hotels avec filtres optionnels")
    public ResponseEntity<Page<HotelResponse>> getHotels(
            @Parameter(description = "Filtrer par ville") @RequestParam(required = false) String city,
            @Parameter(description = "Filtrer par pays") @RequestParam(required = false) String country,
            @Parameter(description = "Recherche textuelle") @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.getHotels(city, country, search, pageable);
    }

    @GetMapping("/hotels/{hotelId}")
    @Operation(summary = "Details d'un hotel", description = "Recupere les details d'un hotel par son ID")
    public ResponseEntity<HotelResponse> getHotelById(
            @Parameter(description = "ID de l'hotel") @PathVariable Long hotelId) {
        return bookingService.getHotelById(hotelId);
    }

    @GetMapping("/hotels/{hotelId}/rooms")
    @Operation(summary = "Chambres disponibles", description = "Recupere les chambres disponibles d'un hotel")
    public ResponseEntity<Page<RoomResponse>> getAvailableRooms(
            @Parameter(description = "ID de l'hotel") @PathVariable Long hotelId,
            @Parameter(description = "Nombre de voyageurs") @RequestParam(required = false) Integer guests,
            @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.getAvailableRooms(hotelId, guests, pageable);
    }

    @GetMapping("/transport/providers")
    @Operation(summary = "Lister les prestataires transport", description = "Recupere la liste des prestataires de transport")
    public ResponseEntity<Page<TransportProviderResponse>> getTransportProviders(
            @Parameter(description = "Filtrer par ville") @RequestParam(required = false) String city,
            @Parameter(description = "Type de transport") @RequestParam(required = false) TransportType type,
            @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.getTransportProviders(city, type, pageable);
    }

    @GetMapping("/transport/providers/{providerId}")
    @Operation(summary = "Details d'un prestataire", description = "Recupere les details d'un prestataire de transport")
    public ResponseEntity<TransportProviderResponse> getTransportProviderById(
            @Parameter(description = "ID du prestataire") @PathVariable Long providerId) {
        return bookingService.getTransportProviderById(providerId);
    }

    @PostMapping("/hotels/book")
    @Operation(summary = "Reserver un hotel", description = "Cree une demande de reservation d'hotel")
    public ResponseEntity<BookingResponse> createHotelBooking(
            @Parameter(description = "Username du client") @RequestParam String clientUsername,
            @RequestBody CreateHotelBookingRequest request) {
        return bookingService.createHotelBooking(clientUsername, request);
    }

    @PostMapping("/transport/book")
    @Operation(summary = "Reserver un transport", description = "Cree une demande de reservation de transport")
    public ResponseEntity<BookingResponse> createTransportBooking(
            @Parameter(description = "Username du client") @RequestParam String clientUsername,
            @RequestBody CreateTransportBookingRequest request) {
        return bookingService.createTransportBooking(clientUsername, request);
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "Mes reservations", description = "Recupere les reservations de l'utilisateur connecte")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @Parameter(description = "Username de l'utilisateur") @RequestParam String username,
            @Parameter(description = "Filtrer par statut") @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "Filtrer par type") @RequestParam(required = false) BookingType type,
            @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.getMyBookings(username, status, type, pageable);
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Details d'une reservation", description = "Recupere les details d'une reservation par ID")
    public ResponseEntity<BookingResponse> getBookingById(
            @Parameter(description = "ID de la reservation") @PathVariable Long bookingId) {
        return bookingService.getBookingById(bookingId);
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Reservation par reference", description = "Recupere une reservation par sa reference")
    public ResponseEntity<BookingResponse> getBookingByReference(
            @Parameter(description = "Reference de la reservation") @PathVariable String reference) {
        return bookingService.getBookingByReference(reference);
    }

    @PostMapping("/{reference}/cancel")
    @Operation(summary = "Annuler une reservation", description = "Annule une reservation (client)")
    public ResponseEntity<BookingResponse> cancelBooking(
            @Parameter(description = "Reference de la reservation") @PathVariable String reference,
            @Parameter(description = "Username du client") @RequestParam String username,
            @RequestBody CancelBookingRequest request) {
        return bookingService.cancelBooking(reference, username, request);
    }

    @GetMapping("/provider/bookings")
    @Operation(summary = "Reservations du prestataire", description = "Recupere les reservations recues par le prestataire")
    public ResponseEntity<Page<BookingResponse>> getProviderBookings(
            @Parameter(description = "Username du prestataire") @RequestParam String providerUsername,
            @Parameter(description = "Filtrer par statut") @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.getProviderBookings(providerUsername, status, pageable);
    }

    @PostMapping("/provider/{reference}/accept")
    @Operation(summary = "Accepter une reservation", description = "Le prestataire accepte une reservation")
    public ResponseEntity<BookingResponse> acceptBooking(
            @Parameter(description = "Reference de la reservation") @PathVariable String reference,
            @Parameter(description = "Username du prestataire") @RequestParam String providerUsername,
            @RequestBody(required = false) AcceptBookingRequest request) {
        return bookingService.acceptBooking(reference, providerUsername, request);
    }

    @PostMapping("/provider/{reference}/reject")
    @Operation(summary = "Refuser une reservation", description = "Le prestataire refuse une reservation")
    public ResponseEntity<BookingResponse> rejectBooking(
            @Parameter(description = "Reference de la reservation") @PathVariable String reference,
            @Parameter(description = "Username du prestataire") @RequestParam String providerUsername,
            @RequestBody RejectBookingRequest request) {
        return bookingService.rejectBooking(reference, providerUsername, request);
    }

    @PostMapping("/provider/complete-by-qr")
    @Operation(summary = "Completer via QR code", description = "Le prestataire scanne le QR code pour confirmer la prestation")
    public ResponseEntity<BookingResponse> completeBookingByQrCode(
            @Parameter(description = "Token QR code") @RequestParam String qrCode,
            @Parameter(description = "Username du prestataire") @RequestParam String providerUsername) {
        return bookingService.completeBookingByQrCode(qrCode, providerUsername);
    }
}
