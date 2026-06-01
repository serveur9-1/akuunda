package org.akuunda.akuundawallet.keycloak.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource
public interface UserRepository extends JpaRepository<Users, String> {

    // LIMIT 1 natif : évite NonUniqueResultException si plusieurs comptes partagent le même username
    @Query(value = "SELECT * FROM users WHERE username = :username ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Users getUsersByUsername(@Param("username") String username);

    @Query(value = "SELECT * FROM users WHERE email = :email ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByEmailOrderByCreatedAtAsc(@Param("email") String email);

    /** Variante insensible à la casse — Keycloak renvoie parfois l'email en majuscule différente. */
    @Query(value = "SELECT * FROM users WHERE LOWER(email) = LOWER(:email) ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByEmailIgnoreCase(@Param("email") String email);

    @Query(value = "SELECT * FROM users WHERE username = :username ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByUsernameOrderByCreatedAtAsc(@Param("username") String username);

    @Query(value = "SELECT * FROM users WHERE LOWER(username) = LOWER(:username) ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByUsernameIgnoreCase(@Param("username") String username);

    @Query(value = "SELECT * FROM users WHERE mobile_phone = :mobilePhone ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByMobilePhoneOrderByCreatedAtAsc(@Param("mobilePhone") String mobilePhone);

    @Query(value = "SELECT * FROM users WHERE LOWER(mobile_phone) = LOWER(:mobilePhone) ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByMobilePhoneIgnoreCase(@Param("mobilePhone") String mobilePhone);

    /**
     * Correspondance par chiffres uniquement (ex. {@code +225 07…} en base vs {@code 0707…} dans le token).
     * Exige au moins 9 chiffres pour limiter les faux positifs.
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE LENGTH(regexp_replace(:raw, '[^0-9]', '', 'g')) >= 9
              AND (
                (LENGTH(regexp_replace(COALESCE(u.mobile_phone, ''), '[^0-9]', '', 'g')) >= 9
                 AND regexp_replace(COALESCE(u.mobile_phone, ''), '[^0-9]', '', 'g') = regexp_replace(:raw, '[^0-9]', '', 'g'))
                OR
                (LENGTH(regexp_replace(COALESCE(u.username, ''), '[^0-9]', '', 'g')) >= 9
                 AND regexp_replace(COALESCE(u.username, ''), '[^0-9]', '', 'g') = regexp_replace(:raw, '[^0-9]', '', 'g'))
                OR
                (LENGTH(regexp_replace(COALESCE(u.mobile_phone, ''), '[^0-9]', '', 'g')) >= 10
                 AND (
                   regexp_replace(COALESCE(u.mobile_phone, ''), '[^0-9]', '', 'g')
                       LIKE '%' || regexp_replace(:raw, '[^0-9]', '', 'g')
                   OR regexp_replace(:raw, '[^0-9]', '', 'g')
                       LIKE '%' || regexp_replace(COALESCE(u.mobile_phone, ''), '[^0-9]', '', 'g')
                 ))
              )
            ORDER BY u.created_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Users> findFirstByPhoneOrUsernameDigits(@Param("raw") String raw);

    List<Users> findAllByUsername(String username);

    @Query(value = "SELECT * FROM users WHERE google_id = :googleId ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Users getUsersByGoogleId(@Param("googleId") String googleId);

    @Query(value = "SELECT * FROM users WHERE google_id = :googleId ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByGoogleIdOrderByCreatedAtAsc(@Param("googleId") String googleId);

    @Query(value = "SELECT * FROM users WHERE facebook_id = :facebookId ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Users getUsersByFacebookId(@Param("facebookId") String facebookId);

    @Query(value = "SELECT * FROM users WHERE facebook_id = :facebookId ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByFacebookIdOrderByCreatedAtAsc(@Param("facebookId") String facebookId);

    @Query(value = "SELECT * FROM users WHERE apple_id = :appleId ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Users getUsersByAppleId(@Param("appleId") String appleId);

    @Query(value = "SELECT * FROM users WHERE apple_id = :appleId ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<Users> findFirstByAppleIdOrderByCreatedAtAsc(@Param("appleId") String appleId);

    long countByEnabledTrue();

    long countByIdentyVerifyFalse();

    long countByCreatedAtBetween(Timestamp start, Timestamp end);

    @org.springframework.data.jpa.repository.Query(
            "SELECT YEAR(u.createdAt), MONTH(u.createdAt), COUNT(u) FROM Users u " +
            "WHERE u.createdAt >= :since " +
            "GROUP BY YEAR(u.createdAt), MONTH(u.createdAt) " +
            "ORDER BY YEAR(u.createdAt), MONTH(u.createdAt)")
    List<Object[]> countByMonth(@org.springframework.data.repository.query.Param("since") java.sql.Timestamp since);

    org.springframework.data.domain.Page<Users> findByEnabled(boolean enabled, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT u FROM Users u WHERE " +
            "LOWER(u.email) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(u.mobilePhone) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(u.firstname) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(u.lastname) LIKE CONCAT('%', :q, '%')")
    org.springframework.data.domain.Page<Users> findBySearch(
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT u FROM Users u WHERE u.enabled = :enabled AND (" +
            "LOWER(u.email) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(u.mobilePhone) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(u.firstname) LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(u.lastname) LIKE CONCAT('%', :q, '%'))")
    org.springframework.data.domain.Page<Users> findByEnabledAndSearch(
            @org.springframework.data.repository.query.Param("enabled") boolean enabled,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);
}
