package org.akuunda.akuundawallet.esim.catalog;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.akuunda.akuundawallet.esim.api.dto.PriceItemDto;
import org.akuunda.akuundawallet.esim.api.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Charge la colonne « Recommended Retail Price (excl. VAT) » depuis la feuille « Bundles prices »
 * (référence produit = « Technical reference », colonne A).
 */
@Component
@Slf4j
public class EsimRrpXlsxCatalogOverlay {

    @Value("${esim.pricing.rrp-xlsx-path:}")
    private String xlsxPath;

    @Value("${esim.pricing.rrp-sheet-name:Bundles prices}")
    private String sheetName;

    /** Ligne d’en-tête Excel (1-based), ligne contenant « Recommended Retail Price (excl. VAT) » */
    @Value("${esim.pricing.rrp-header-row:7}")
    private int headerRowOneBased;

    /** Colonne « Technical reference » (1-based, A=1) */
    @Value("${esim.pricing.rrp-column-product:1}")
    private int productColOneBased;

    /** Colonne « Recommended Retail Price (excl. VAT) » (1-based, H=8) */
    @Value("${esim.pricing.rrp-column-rrp:8}")
    private int rrpColOneBased;

    @Value("${esim.pricing.rrp-currency:EUR}")
    private String defaultCurrency;

    private volatile Map<String, BigDecimal> priceByProductId = Map.of();

    @PostConstruct
    void loadOnStartup() {
        reload();
    }

    /**
     * Recharge la map depuis le fichier (appelable après mise à jour du classeur).
     */
    public synchronized void reload() {
        if (xlsxPath == null || xlsxPath.isBlank()) {
            priceByProductId = Map.of();
            log.debug("esim.pricing.rrp-xlsx-path vide — pas de fusion prix retail.");
            return;
        }
        Path path = Paths.get(xlsxPath.trim());
        if (!Files.isRegularFile(path)) {
            log.warn("Grille tarifaire introuvable ou non fichier: {}", path.toAbsolutePath());
            priceByProductId = Map.of();
            return;
        }
        try {
            Map<String, BigDecimal> loaded = loadFromPath(path);
            priceByProductId = Collections.unmodifiableMap(loaded);
            log.info("Grille tarifaire RRP chargée: {} entrées depuis {}", loaded.size(), path.getFileName());
        } catch (Exception e) {
            log.error("Impossible de charger la grille tarifaire xlsx: {}", e.getMessage(), e);
            priceByProductId = Map.of();
        }
    }

    private Map<String, BigDecimal> loadFromPath(Path path) throws Exception {
        Map<String, BigDecimal> out = new HashMap<>();
        try (InputStream in = new FileInputStream(path.toFile());
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                log.warn("Feuille Excel introuvable: {}", sheetName);
                return out;
            }
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            DataFormatter fmt = new DataFormatter();

            int headerRow0 = Math.max(0, headerRowOneBased - 1);
            int prodCol0 = Math.max(0, productColOneBased - 1);
            int rrpCol0 = Math.max(0, rrpColOneBased - 1);

            int last = sheet.getLastRowNum();
            for (int r = headerRow0 + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Cell idCell = row.getCell(prodCol0);
                Cell rrpCell = row.getCell(rrpCol0);
                String id = cellText(idCell, fmt, evaluator).trim();
                if (id.isEmpty()) {
                    continue;
                }
                BigDecimal rrp = cellMoney(rrpCell, evaluator);
                if (rrp == null) {
                    continue;
                }
                out.put(id, rrp);
            }
        }
        return out;
    }

    private static String cellText(Cell cell, DataFormatter fmt, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return fmt.formatCellValue(cell, evaluator).trim();
    }

    private static BigDecimal cellMoney(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return roundMoney(BigDecimal.valueOf(cell.getNumericCellValue()));
                case STRING:
                    String s = cell.getStringCellValue();
                    if (s == null || s.isBlank()) {
                        return null;
                    }
                    return roundMoney(new BigDecimal(s.trim().replace(',', '.')));
                case FORMULA:
                    CellValue cv = evaluator.evaluate(cell);
                    if (cv == null) {
                        return null;
                    }
                    return switch (cv.getCellType()) {
                        case NUMERIC -> roundMoney(BigDecimal.valueOf(cv.getNumberValue()));
                        case STRING -> {
                            String t = cv.getStringValue();
                            if (t == null || t.isBlank()) {
                                yield null;
                            }
                            try {
                                yield roundMoney(new BigDecimal(t.trim().replace(',', '.')));
                            } catch (NumberFormatException ex) {
                                yield null;
                            }
                        }
                        default -> null;
                    };
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal roundMoney(BigDecimal v) {
        return v.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    public void apply(List<ProductDto> products) {
        if (products == null || products.isEmpty() || priceByProductId.isEmpty()) {
            return;
        }
        for (ProductDto p : products) {
            if (p.getProductDefinition() == null) {
                continue;
            }
            String pid = p.getProductDefinition().getProductId();
            if (pid == null || pid.isBlank()) {
                continue;
            }
            BigDecimal rrp = priceByProductId.get(pid.trim());
            if (rrp != null) {
                p.setRecommendedRetailPriceExclVat(rrp);
                p.setRecommendedRetailPriceCurrency(defaultCurrency);
                fillZeroCentAmountsFromRrp(p, rrp);
            }
        }
    }

    /**
     * Transatel renvoie souvent {@code amount: 0} sur les frais d’abonnement alors que le prix retail
     * est dans notre grille Excel (EUR HT). On recopie le RRP en centimes pour que {@code prices}
     * reste cohérent pour les clients qui lisent {@code subscriptionFee}/{@code renewalFee}.
     */
    private void fillZeroCentAmountsFromRrp(ProductDto p, BigDecimal rrpEur) {
        if (p.getPrices() == null || rrpEur == null) {
            return;
        }
        int cents = rrpEur.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        fillNestedPriceZeros(p.getPrices().getSubscriptionFee(), cents);
        fillNestedPriceZeros(p.getPrices().getRenewalFee(), cents);
    }

    private void fillNestedPriceZeros(List<List<PriceItemDto>> nested, int cents) {
        if (nested == null) {
            return;
        }
        for (List<PriceItemDto> tier : nested) {
            if (tier == null) {
                continue;
            }
            for (PriceItemDto item : tier) {
                if (item == null || item.getAmount() != 0) {
                    continue;
                }
                if (!"CENTS".equalsIgnoreCase(item.getUnit())) {
                    continue;
                }
                String cur = item.getCurrency();
                if (cur != null && !cur.isBlank() && !defaultCurrency.equalsIgnoreCase(cur)) {
                    continue;
                }
                item.setAmount(cents);
                if (cur == null || cur.isBlank()) {
                    item.setCurrency(defaultCurrency);
                }
            }
        }
    }
}
