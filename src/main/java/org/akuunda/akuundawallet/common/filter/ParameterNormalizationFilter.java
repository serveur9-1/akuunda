package org.akuunda.akuundawallet.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Filtre pour normaliser les paramètres de requête en supprimant les espaces
 * dans les noms de paramètres (ex: "fromDate " -> "fromDate")
 */
@Component
@Order(1)
public class ParameterNormalizationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Exclure les chemins Swagger/OpenAPI pour éviter les problèmes
        String requestPath = request.getRequestURI();
        if (requestPath != null && (
                requestPath.startsWith("/swagger-ui") ||
                requestPath.startsWith("/v3/api-docs") ||
                requestPath.startsWith("/swagger-ui.html") ||
                requestPath.startsWith("/swagger-resources") ||
                requestPath.startsWith("/webjars")
        )) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Vérifier si la requête a des paramètres avec des espaces
        boolean needsNormalization = request.getParameterMap().keySet().stream()
                .anyMatch(key -> key.contains(" ") || key.endsWith(" "));
        
        if (needsNormalization) {
            // Créer une requête wrapper avec des paramètres normalisés
            HttpServletRequest normalizedRequest = new HttpServletRequestWrapper(request) {
                private Map<String, String[]> normalizedParams = null;
                
                @Override
                public Map<String, String[]> getParameterMap() {
                    if (normalizedParams == null) {
                        normalizedParams = new HashMap<>();
                        Map<String, String[]> originalParams = super.getParameterMap();
                        
                        for (Map.Entry<String, String[]> entry : originalParams.entrySet()) {
                            String normalizedKey = entry.getKey().trim();
                            normalizedParams.put(normalizedKey, entry.getValue());
                        }
                    }
                    return Collections.unmodifiableMap(normalizedParams);
                }
                
                @Override
                public String getParameter(String name) {
                    String[] values = getParameterMap().get(name);
                    return (values != null && values.length > 0) ? values[0] : null;
                }
                
                @Override
                public String[] getParameterValues(String name) {
                    return getParameterMap().get(name);
                }
            };
            
            filterChain.doFilter(normalizedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}

