package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dto.CancelConditionalPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentResponse;
import org.akuunda.akuundawallet.wallet.api.dto.QRCodeValidationRequest;
import org.akuunda.akuundawallet.wallet.api.dto.booking.*;
import org.akuunda.akuundawallet.wallet.api.entities.*;
import org.akuunda.akuundawallet.wallet.api.enums.BookingStatus;
import org.akuunda.akuundawallet.wallet.api.enums.BookingType;
import org.akuunda.akuundawallet.wallet.api.enums.TransportType;
import org.akuunda.akuundawallet.wallet.repository.*;
import org.akuunda.akuundawallet.wallet.service.BookingService;
import org.akuunda.akuundawallet.wallet.service.ConditionalPaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final TransportProviderRepository transportProviderRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ConditionalPaymentService conditionalPaymentService;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════════════════════════════════════
    // ── Hôtels
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<Page<HotelResponse>> getHotels(String city, String country, String search, Pageable pageable) {
        log.info("🏨 Fetching hotels: city={}, country={}, search={}", city, country, search);

        Page<Hotel> hotels;

        if (search != null && !search.isBlank()) {
            hotels = hotelRepository.searchHotels(search, pageable);
        } else if (city != null && country != null) {
            hotels = hotelRepository.findByCityIgnoreCaseAndCountryIgnoreCaseAndIsActiveTrue(city, country, pageable);
        } else if (city != null) {
            hotels = hotelRepository.findByCityIgnoreCaseAndIsActiveTrue(city, pageable);
        } else if (country != null) {
            hotels = hotelRepository.findByCountryIgnoreCaseAndIsActiveTrue(country, pageable);
        } else {
            hotels = hotelRepository.findByIsActiveTrue(pageable);
        }

        return ResponseEntity.ok(hotels.map(this::mapToHotelResponse));
    }

    @Override
    public ResponseEntity<HotelResponse> getHotelById(Long hotelId) {
        log.info("🏨 Fetching hotel: id={}", hotelId);

        return hotelRepository.findByIdAndIsActiveTrue(hotelId)
                .map(hotel -> ResponseEntity.ok(mapToHotelResponse(hotel)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Page<RoomResponse>> getAvailableRooms(Long hotelId, Integer guests, Pageable pageable) {
        log.info("🛏️ Fetching available rooms: hotelId={}, guests={}", hotelId, guests);

        List<Room> rooms = guests != null
                ? roomRepository.findAvailableRooms(hotelId, guests)
                : roomRepository.findByHotelIdAndIsAvailableTrue(hotelId);

        Page<Room> roomPage = new PageImpl<>(rooms, pageable, rooms.size());

        return ResponseEntity.ok(roomPage.map(this::mapToRoomResponse));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ── Transport
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<Page<TransportProviderResponse>> getTransportProviders(String city, TransportType type, Pageable pageable) {
        log.info("🚗 Fetching transport providers: city={}, type={}", city, type);

        Page<TransportProvider> providers;

        if (type != null) {
            providers = transportProviderRepository.findByServiceType(type.name(), pageable);
        } else if (city != null) {
            providers = transportProviderRepository.findByCityIgnoreCaseAndIsAvailableTrue(city, pageable);
        } else {
            providers = transportProviderRepository.findByIsAvailableTrue(pageable);
        }

        return ResponseEntity.ok(providers.map(this::mapToTransportProviderResponse));
    }

    @Override
    public ResponseEntity<TransportProviderResponse> getTransportProviderById(Long providerId) {
        log.info("🚗 Fetching transport provider: id={}", providerId);

        return transportProviderRepository.findByIdAndIsAvailableTrue(providerId)
                .map(provider -> ResponseEntity.ok(mapToTransportProviderResponse(provider)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ── Réservations Client
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public ResponseEntity<BookingResponse> createHotelBooking(String clientUsername, CreateHotelBookingRequest request) {
        log.info("📝 Creating hotel booking: client={}, hotel={}, room={}", clientUsername, request.getHotelId(), request.getRoomId());

        try {
            // 1. Valider l'utilisateur
            Users client = userRepository.getUsersByUsername(clientUsername);
            if (client == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BookingResponse.builder().error("Client non trouvé").build());
            }

            // 2. Valider l'hôtel et la chambre
            Hotel hotel = hotelRepository.findByIdAndIsActiveTrue(request.getHotelId()).orElse(null);
            if (hotel == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BookingResponse.builder().error("Hôtel non trouvé").build());
            }

            Room room = roomRepository.findById(request.getRoomId()).orElse(null);
            if (room == null || !room.getIsAvailable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BookingResponse.builder().error("Chambre non disponible").build());
            }

            // 3. Calculer le nombre de nuits et le montant
            long nights = ChronoUnit.DAYS.between(request.getCheckInDate().toLocalDate(), request.getCheckOutDate().toLocalDate());
            if (nights < 1) {
                return ResponseEntity.badRequest()
                        .body(BookingResponse.builder().error("La date de départ doit être après la date d'arrivée").build());
            }

            int numberOfRooms = request.getNumberOfRooms() != null ? request.getNumberOfRooms() : 1;
            double amount = room.getPricePerNight() * nights * numberOfRooms;
            double serviceFee = amount * 0.05;
            double totalAmount = amount + serviceFee;

            // 4. Créer la réservation
            Booking booking = Booking.builder()
                    .type(BookingType.HOTEL)
                    .status(BookingStatus.PENDING)
                    .user(client)
                    .userName(client.getFirstname() + " " + client.getLastname())
                    .userPhone(client.getMobilePhone())
                    .userEmail(client.getEmail())
                    .providerUser(hotel.getOwner())
                    .providerName(hotel.getName())
                    .amount(amount)
                    .serviceFee(serviceFee)
                    .totalAmount(totalAmount)
                    .currency(room.getCurrency())
                    .hotel(hotel)
                    .room(room)
                    .checkInDate(request.getCheckInDate())
                    .checkOutDate(request.getCheckOutDate())
                    .numberOfNights((int) nights)
                    .numberOfGuests(request.getNumberOfGuests())
                    .numberOfRooms(numberOfRooms)
                    .userNote(request.getSpecialRequests())
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();

            booking = bookingRepository.save(booking);
            log.info("✅ Hotel booking created: reference={}", booking.getReference());

            return ResponseEntity.ok(mapToBookingResponse(booking));

        } catch (Exception e) {
            log.error("❌ Error creating hotel booking: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BookingResponse.builder().error("Erreur lors de la création de la réservation").build());
        }
    }

    @Override
    @Transactional
    public ResponseEntity<BookingResponse> createTransportBooking(String clientUsername, CreateTransportBookingRequest request) {
        log.info("📝 Creating transport booking: client={}, provider={}, vehicle={}",
                clientUsername, request.getProviderId(), request.getVehicleId());

        try {
            // 1. Valider l'utilisateur
            Users client = userRepository.getUsersByUsername(clientUsername);
            if (client == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BookingResponse.builder().error("Client non trouvé").build());
            }

            // 2. Valider le prestataire et le véhicule
            TransportProvider provider = transportProviderRepository.findByIdAndIsAvailableTrue(request.getProviderId()).orElse(null);
            if (provider == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BookingResponse.builder().error("Prestataire non trouvé").build());
            }

            Vehicle vehicle = vehicleRepository.findById(request.getVehicleId()).orElse(null);
            if (vehicle == null || !vehicle.getIsAvailable()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(BookingResponse.builder().error("Véhicule non disponible").build());
            }

            if (request.getNumberOfPassengers() > vehicle.getMaxPassengers()) {
                return ResponseEntity.badRequest()
                        .body(BookingResponse.builder().error("Nombre de passagers supérieur à la capacité du véhicule").build());
            }

            // 3. Calculer le montant
            double amount = vehicle.getBasePrice();
            double serviceFee = amount * 0.10;
            double totalAmount = amount + serviceFee;

            // 4. Créer la réservation
            Booking booking = Booking.builder()
                    .type(BookingType.TRANSPORT)
                    .status(BookingStatus.PENDING)
                    .user(client)
                    .userName(client.getFirstname() + " " + client.getLastname())
                    .userPhone(client.getMobilePhone())
                    .userEmail(client.getEmail())
                    .providerUser(provider.getOwner())
                    .providerName(provider.getName())
                    .amount(amount)
                    .serviceFee(serviceFee)
                    .totalAmount(totalAmount)
                    .currency(vehicle.getCurrency())
                    .transportProvider(provider)
                    .vehicle(vehicle)
                    .transportType(request.getTransportType())
                    .pickupAddress(request.getPickupAddress())
                    .pickupLatitude(request.getPickupLatitude())
                    .pickupLongitude(request.getPickupLongitude())
                    .dropoffAddress(request.getDropoffAddress())
                    .dropoffLatitude(request.getDropoffLatitude())
                    .dropoffLongitude(request.getDropoffLongitude())
                    .pickupDateTime(request.getPickupDateTime())
                    .flightNumber(request.getFlightNumber())
                    .numberOfPassengers(request.getNumberOfPassengers())
                    .numberOfLuggage(request.getNumberOfLuggage())
                    .userNote(request.getSpecialRequests())
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();

            booking = bookingRepository.save(booking);
            log.info("✅ Transport booking created: reference={}", booking.getReference());

            return ResponseEntity.ok(mapToBookingResponse(booking));

        } catch (Exception e) {
            log.error("❌ Error creating transport booking: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BookingResponse.builder().error("Erreur lors de la création de la réservation").build());
        }
    }

    @Override
    public ResponseEntity<Page<BookingResponse>> getMyBookings(String username, BookingStatus status, BookingType type, Pageable pageable) {
        log.info("📋 Fetching bookings for user: username={}, status={}, type={}", username, status, type);

        Page<Booking> bookings;

        if (status != null) {
            bookings = bookingRepository.findByUserUsernameAndStatus(username, status, pageable);
        } else if (type != null) {
            bookings = bookingRepository.findByUserUsernameAndType(username, type, pageable);
        } else {
            bookings = bookingRepository.findByUserUsername(username, pageable);
        }

        return ResponseEntity.ok(bookings.map(this::mapToBookingResponse));
    }

    @Override
    public ResponseEntity<BookingResponse> getBookingById(Long bookingId) {
        log.info("📋 Fetching booking: id={}", bookingId);

        return bookingRepository.findById(bookingId)
                .map(booking -> ResponseEntity.ok(mapToBookingResponse(booking)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<BookingResponse> getBookingByReference(String reference) {
        log.info("📋 Fetching booking: reference={}", reference);

        return bookingRepository.findByReference(reference)
                .map(booking -> ResponseEntity.ok(mapToBookingResponse(booking)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @Transactional
    public ResponseEntity<BookingResponse> cancelBooking(String bookingReference, String username, CancelBookingRequest request) {
        log.info("❌ Cancelling booking: reference={}, user={}", bookingReference, username);

        try {
            Booking booking = bookingRepository.findByReference(bookingReference).orElse(null);
            if (booking == null) {
                return ResponseEntity.notFound().build();
            }

            if (!booking.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(BookingResponse.builder().error("Non autorisé").build());
            }

            if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.ACCEPTED) {
                return ResponseEntity.badRequest()
                        .body(BookingResponse.builder().error("Cette réservation ne peut plus être annulée").build());
            }

            if (booking.getConditionalPaymentCode() != null) {
                conditionalPaymentService.cancelPayment(booking.getConditionalPaymentCode(),
                        new CancelConditionalPaymentRequest(request.getReason()));
            }

            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(LocalDateTime.now());
            booking.setCancellationReason(request.getReason());
            booking = bookingRepository.save(booking);

            log.info("✅ Booking cancelled: reference={}", bookingReference);

            return ResponseEntity.ok(mapToBookingResponse(booking));

        } catch (Exception e) {
            log.error("❌ Error cancelling booking: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BookingResponse.builder().error("Erreur lors de l'annulation").build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ── Réservations Prestataire
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<Page<BookingResponse>> getProviderBookings(String providerUsername, BookingStatus status, Pageable pageable) {
        log.info("📋 Fetching provider bookings: provider={}, status={}", providerUsername, status);

        Page<Booking> bookings;

        if (status != null) {
            bookings = bookingRepository.findByProviderUsernameAndStatus(providerUsername, status, pageable);
        } else {
            bookings = bookingRepository.findByProviderUsername(providerUsername, pageable);
        }

        return ResponseEntity.ok(bookings.map(this::mapToBookingResponse));
    }

    @Override
    @Transactional
    public ResponseEntity<BookingResponse> acceptBooking(String bookingReference, String providerUsername, AcceptBookingRequest request) {
        log.info("✅ Accepting booking: reference={}, provider={}", bookingReference, providerUsername);

        try {
            Booking booking = bookingRepository.findByReference(bookingReference).orElse(null);
            if (booking == null) {
                return ResponseEntity.notFound().build();
            }

            if (!booking.getProviderUser().getUsername().equals(providerUsername)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(BookingResponse.builder().error("Non autorisé").build());
            }

            if (booking.getStatus() != BookingStatus.PENDING) {
                return ResponseEntity.badRequest()
                        .body(BookingResponse.builder().error("Cette réservation ne peut plus être acceptée").build());
            }

            // Créer le paiement conditionnel
            String serviceType = booking.getType() == BookingType.HOTEL ? "HOTEL" : "TRANSPORT";
            String description = booking.getType() == BookingType.HOTEL
                    ? "Réservation " + booking.getHotel().getName() + " - " + booking.getNumberOfNights() + " nuit(s)"
                    : "Transport " + booking.getTransportProvider().getName() + " - " + booking.getPickupAddress();

            LocalDateTime serviceStartDate = booking.getType() == BookingType.HOTEL
                    ? booking.getCheckInDate()
                    : booking.getPickupDateTime();

            ConditionalPaymentRequest paymentRequest = new ConditionalPaymentRequest();
            paymentRequest.setVendorUsername(providerUsername);
            paymentRequest.setServiceType(serviceType);
            paymentRequest.setDescription(description);
            paymentRequest.setAmount(booking.getTotalAmount());
            paymentRequest.setCurrency(booking.getCurrency());
            paymentRequest.setServiceStartDate(serviceStartDate);
            paymentRequest.setCancellationDeadline(serviceStartDate.minusHours(24));

            ResponseEntity<ConditionalPaymentResponse> paymentResponse =
                    conditionalPaymentService.createConditionalPayment(booking.getUser().getUsername(), paymentRequest);

            if (!paymentResponse.getStatusCode().is2xxSuccessful() || paymentResponse.getBody() == null) {
                log.error("❌ Failed to create conditional payment for booking {}", bookingReference);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(BookingResponse.builder().error("Erreur lors de la création du paiement conditionnel").build());
            }

            ConditionalPaymentResponse cpResponse = paymentResponse.getBody();

            booking.setStatus(BookingStatus.ACCEPTED);
            booking.setAcceptedAt(LocalDateTime.now());
            booking.setProviderNote(request != null ? request.getNote() : null);
            booking.setConditionalPaymentId(cpResponse.getId());
            booking.setConditionalPaymentCode(cpResponse.getPaymentCode());
            booking.setQrCode(cpResponse.getQrCodeToken());
            booking.setQrCodeUrl(cpResponse.getQrCodeUrl());
            booking.setFundsLocked(true);
            booking.setFundsLockedAt(LocalDateTime.now());

            booking = bookingRepository.save(booking);

            log.info("✅ Booking accepted: reference={}, paymentCode={}", bookingReference, cpResponse.getPaymentCode());

            return ResponseEntity.ok(mapToBookingResponse(booking));

        } catch (Exception e) {
            log.error("❌ Error accepting booking: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BookingResponse.builder().error("Erreur lors de l'acceptation").build());
        }
    }

    @Override
    @Transactional
    public ResponseEntity<BookingResponse> rejectBooking(String bookingReference, String providerUsername, RejectBookingRequest request) {
        log.info("❌ Rejecting booking: reference={}, provider={}", bookingReference, providerUsername);

        try {
            Booking booking = bookingRepository.findByReference(bookingReference).orElse(null);
            if (booking == null) {
                return ResponseEntity.notFound().build();
            }

            if (!booking.getProviderUser().getUsername().equals(providerUsername)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(BookingResponse.builder().error("Non autorisé").build());
            }

            if (booking.getStatus() != BookingStatus.PENDING) {
                return ResponseEntity.badRequest()
                        .body(BookingResponse.builder().error("Cette réservation ne peut plus être refusée").build());
            }

            booking.setStatus(BookingStatus.REJECTED);
            booking.setRejectedAt(LocalDateTime.now());
            booking.setRejectionReason(request.getReason());

            booking = bookingRepository.save(booking);

            log.info("✅ Booking rejected: reference={}", bookingReference);

            return ResponseEntity.ok(mapToBookingResponse(booking));

        } catch (Exception e) {
            log.error("❌ Error rejecting booking: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BookingResponse.builder().error("Erreur lors du refus").build());
        }
    }

    @Override
    @Transactional
    public ResponseEntity<BookingResponse> completeBookingByQrCode(String qrCode, String providerUsername) {
        log.info("🎉 Completing booking by QR code: provider={}", providerUsername);

        try {
            QRCodeValidationRequest validationRequest = new QRCodeValidationRequest();
            validationRequest.setQrCodeToken(qrCode);
            validationRequest.setScannedBy(providerUsername);

            ResponseEntity<ConditionalPaymentResponse> validationResponse =
                    conditionalPaymentService.validateQRCode(validationRequest);

            if (!validationResponse.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.status(validationResponse.getStatusCode())
                        .body(BookingResponse.builder().error("QR code invalide ou expiré").build());
            }

            ConditionalPaymentResponse cpResponse = validationResponse.getBody();

            Booking booking = bookingRepository.findByConditionalPaymentCode(cpResponse.getPaymentCode()).orElse(null);

            if (booking == null) {
                return ResponseEntity.notFound().build();
            }

            booking.setStatus(BookingStatus.COMPLETED);
            booking.setCompletedAt(LocalDateTime.now());
            booking.setFundsReleasedAt(LocalDateTime.now());

            booking = bookingRepository.save(booking);

            log.info("✅ Booking completed: reference={}", booking.getReference());

            return ResponseEntity.ok(mapToBookingResponse(booking));

        } catch (Exception e) {
            log.error("❌ Error completing booking: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BookingResponse.builder().error("Erreur lors de la validation").build());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ── Mappers
    // ═══════════════════════════════════════════════════════════════════════════

    private HotelResponse mapToHotelResponse(Hotel hotel) {
        return HotelResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .description(hotel.getDescription())
                .address(hotel.getAddress())
                .city(hotel.getCity())
                .country(hotel.getCountry())
                .latitude(hotel.getLatitude())
                .longitude(hotel.getLongitude())
                .imageUrl(hotel.getImageUrl())
                .images(parseJsonArray(hotel.getImages()))
                .rating(hotel.getRating())
                .reviewCount(hotel.getReviewCount())
                .amenities(parseJsonArray(hotel.getAmenities()))
                .rooms(hotel.getRooms() != null ? hotel.getRooms().stream().map(this::mapToRoomResponse).toList() : Collections.emptyList())
                .phone(hotel.getPhone())
                .email(hotel.getEmail())
                .isVerified(hotel.getIsVerified())
                .ownerUsername(hotel.getOwner() != null ? hotel.getOwner().getUsername() : null)
                .build();
    }

    private RoomResponse mapToRoomResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .pricePerNight(room.getPricePerNight())
                .currency(room.getCurrency())
                .maxGuests(room.getMaxGuests())
                .amenities(parseJsonArray(room.getAmenities()))
                .images(parseJsonArray(room.getImages()))
                .isAvailable(room.getIsAvailable())
                .totalRooms(room.getTotalRooms())
                .build();
    }

    private TransportProviderResponse mapToTransportProviderResponse(TransportProvider provider) {
        return TransportProviderResponse.builder()
                .id(provider.getId())
                .name(provider.getName())
                .description(provider.getDescription())
                .photoUrl(provider.getPhotoUrl())
                .phone(provider.getPhone())
                .email(provider.getEmail())
                .city(provider.getCity())
                .country(provider.getCountry())
                .rating(provider.getRating())
                .reviewCount(provider.getReviewCount())
                .completedTrips(provider.getCompletedTrips())
                .serviceAreas(parseJsonArray(provider.getServiceAreas()))
                .serviceTypes(parseJsonArray(provider.getServiceTypes()))
                .vehicles(provider.getVehicles() != null ? provider.getVehicles().stream().map(this::mapToVehicleResponse).toList() : Collections.emptyList())
                .isVerified(provider.getIsVerified())
                .isAvailable(provider.getIsAvailable())
                .ownerUsername(provider.getOwner() != null ? provider.getOwner().getUsername() : null)
                .build();
    }

    private VehicleResponse mapToVehicleResponse(Vehicle vehicle) {
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .type(vehicle.getType())
                .brand(vehicle.getBrand())
                .model(vehicle.getModel())
                .plateNumber(vehicle.getPlateNumber())
                .maxPassengers(vehicle.getMaxPassengers())
                .maxLuggage(vehicle.getMaxLuggage())
                .pricePerKm(vehicle.getPricePerKm())
                .basePrice(vehicle.getBasePrice())
                .currency(vehicle.getCurrency())
                .images(parseJsonArray(vehicle.getImages()))
                .hasAirConditioning(vehicle.getHasAirConditioning())
                .hasWifi(vehicle.getHasWifi())
                .isAvailable(vehicle.getIsAvailable())
                .build();
    }

    private BookingResponse mapToBookingResponse(Booking booking) {
        BookingResponse.BookingResponseBuilder builder = BookingResponse.builder()
                .id(booking.getId())
                .reference(booking.getReference())
                .type(booking.getType())
                .status(booking.getStatus())
                .userId(booking.getUser() != null ? booking.getUser().getUsername() : null)
                .userName(booking.getUserName())
                .userPhone(booking.getUserPhone())
                .userEmail(booking.getUserEmail())
                .providerId(booking.getProviderUser() != null ? booking.getProviderUser().getUsername() : null)
                .providerName(booking.getProviderName())
                .amount(booking.getAmount())
                .serviceFee(booking.getServiceFee())
                .totalAmount(booking.getTotalAmount())
                .currency(booking.getCurrency())
                .qrCode(booking.getQrCode())
                .qrCodeUrl(booking.getQrCodeUrl())
                .conditionalPaymentId(booking.getConditionalPaymentId())
                .conditionalPaymentCode(booking.getConditionalPaymentCode())
                .fundsLocked(booking.getFundsLocked())
                .fundsLockedAt(booking.getFundsLockedAt())
                .fundsReleasedAt(booking.getFundsReleasedAt())
                .createdAt(booking.getCreatedAt())
                .acceptedAt(booking.getAcceptedAt())
                .rejectedAt(booking.getRejectedAt())
                .cancelledAt(booking.getCancelledAt())
                .completedAt(booking.getCompletedAt())
                .expiresAt(booking.getExpiresAt())
                .userNote(booking.getUserNote())
                .providerNote(booking.getProviderNote())
                .cancellationReason(booking.getCancellationReason())
                .rejectionReason(booking.getRejectionReason());

        if (booking.getType() == BookingType.HOTEL && booking.getHotel() != null) {
            builder.hotelDetails(BookingResponse.HotelBookingDetails.builder()
                    .hotelId(booking.getHotel().getId())
                    .hotelName(booking.getHotel().getName())
                    .hotelAddress(booking.getHotel().getAddress())
                    .hotelImageUrl(booking.getHotel().getImageUrl())
                    .roomId(booking.getRoom() != null ? booking.getRoom().getId() : null)
                    .roomName(booking.getRoom() != null ? booking.getRoom().getName() : null)
                    .checkInDate(booking.getCheckInDate())
                    .checkOutDate(booking.getCheckOutDate())
                    .numberOfNights(booking.getNumberOfNights())
                    .numberOfGuests(booking.getNumberOfGuests())
                    .numberOfRooms(booking.getNumberOfRooms())
                    .pricePerNight(booking.getRoom() != null ? booking.getRoom().getPricePerNight() : null)
                    .specialRequests(booking.getUserNote())
                    .build());
        }

        if (booking.getType() == BookingType.TRANSPORT && booking.getTransportProvider() != null) {
            builder.transportDetails(BookingResponse.TransportBookingDetails.builder()
                    .providerId(booking.getTransportProvider().getId())
                    .providerName(booking.getTransportProvider().getName())
                    .providerPhotoUrl(booking.getTransportProvider().getPhotoUrl())
                    .vehicleId(booking.getVehicle() != null ? booking.getVehicle().getId() : null)
                    .vehicleType(booking.getVehicle() != null ? booking.getVehicle().getType() : null)
                    .vehicleBrand(booking.getVehicle() != null ? booking.getVehicle().getBrand() : null)
                    .vehicleModel(booking.getVehicle() != null ? booking.getVehicle().getModel() : null)
                    .transportType(booking.getTransportType())
                    .pickupAddress(booking.getPickupAddress())
                    .pickupLatitude(booking.getPickupLatitude())
                    .pickupLongitude(booking.getPickupLongitude())
                    .dropoffAddress(booking.getDropoffAddress())
                    .dropoffLatitude(booking.getDropoffLatitude())
                    .dropoffLongitude(booking.getDropoffLongitude())
                    .pickupDateTime(booking.getPickupDateTime())
                    .flightNumber(booking.getFlightNumber())
                    .numberOfPassengers(booking.getNumberOfPassengers())
                    .numberOfLuggage(booking.getNumberOfLuggage())
                    .estimatedDistance(booking.getEstimatedDistance())
                    .estimatedDuration(booking.getEstimatedDuration())
                    .specialRequests(booking.getUserNote())
                    .build());
        }

        return builder.build();
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
