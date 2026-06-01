package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.List;
import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource
public interface WalletRepository extends JpaRepository<Wallet,String> {
    List<Wallet> findWalletByUsers(Users users);

    // LIMIT 1 natif conservé : plusieurs wallets peuvent partager une adresse (comptes admin)
    @Query(value = "SELECT * FROM wallet WHERE address = :address LIMIT 1", nativeQuery = true)
    Wallet findByAddress(@Param("address") String address);

    // SpEL :#{#users.userId} évite le merge de l'entité détachée.
    // JOIN FETCH u.countryCurrency : évite le SELECT secondaire déclenché par l'association EAGER.
    @Query("SELECT w FROM Wallet w LEFT JOIN FETCH w.users u LEFT JOIN FETCH u.countryCurrency LEFT JOIN FETCH w.currency WHERE w.users.userId = :#{#users.userId}")
    Wallet findByUsers(@Param("users") Users users);

    // Idem : JOIN FETCH inclut users + countryCurrency en une query au lieu de 3 SELECT séparés.
    @Query("SELECT w FROM Wallet w LEFT JOIN FETCH w.users u LEFT JOIN FETCH u.countryCurrency LEFT JOIN FETCH w.currency WHERE w.users.username = :username")
    Wallet findByUsersUsername(@Param("username") String username);

    // Remplace findById() dans les schedulers : JOIN FETCH évite les SELECT secondaires EAGER.
    @Query("SELECT w FROM Wallet w LEFT JOIN FETCH w.users u LEFT JOIN FETCH u.countryCurrency LEFT JOIN FETCH w.currency WHERE w.id = :id")
    Optional<Wallet> findByIdWithFetch(@Param("id") String id);

    // Remplace findAll() dans WalletBalanceMonitorTask : sans JOIN FETCH, findAll() génère N SELECT users
    // + N SELECT country_currency à cause des associations EAGER. Avec 491 wallets = 982 SELECTs parasites.
    @Query("SELECT w FROM Wallet w LEFT JOIN FETCH w.users u LEFT JOIN FETCH u.countryCurrency LEFT JOIN FETCH w.currency")
    List<Wallet> findAllWithFetch();

    // Bulk-load wallets pour une liste d'utilisateurs en une seule requête.
    // Remplace N appels findByUsers(u) en boucle dans le backoffice (1 SELECT par user → 1 SELECT total).
    @Query("SELECT w FROM Wallet w LEFT JOIN FETCH w.users u LEFT JOIN FETCH u.countryCurrency LEFT JOIN FETCH w.currency WHERE u IN :users")
    List<Wallet> findByUsersIn(@Param("users") List<Users> users);

    @Query("SELECT w.currency.countryCode, w.currency.countryName, w.currency.continentName, w.currency.currencyCode, COUNT(DISTINCT w.users.userId) " +
           "FROM Wallet w WHERE w.currency IS NOT NULL AND w.users IS NOT NULL " +
           "GROUP BY w.currency.countryCode, w.currency.countryName, w.currency.continentName, w.currency.currencyCode")
    List<Object[]> countUsersByCountry();

    /** Returns (mobilePhone, username, countryCode, countryName, continentName, currencyCode) — one row per wallet */
    @Query("SELECT w.users.mobilePhone, w.users.username, w.currency.countryCode, w.currency.countryName, w.currency.continentName, w.currency.currencyCode " +
           "FROM Wallet w WHERE w.currency IS NOT NULL AND w.users IS NOT NULL")
    List<Object[]> getUsersToCountry();
}
