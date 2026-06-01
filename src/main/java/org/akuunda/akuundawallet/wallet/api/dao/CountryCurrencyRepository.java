package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.List;

@CrossOrigin("*")
@RepositoryRestResource
public interface CountryCurrencyRepository extends JpaRepository<CountryCurrency,Long> {

        CountryCurrency findCountryCurrencyByCountryCode(String countryCode);
        CountryCurrency findCountryCurrencyByCallingCode(int callingCode);

        List<CountryCurrency> findCountryCurrencyByIsActivated(boolean isActivated);

        List<CountryCurrency> findCountryCurrencyByContinentName(String continentName);

        List<CountryCurrency> findCountryCurrencyByCurrencyCode(String currencyCode);
}
