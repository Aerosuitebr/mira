package com.prospectportal.module.ingestion.rf;

import java.util.Locale;
import java.util.Set;

/**
 * Layout oficial RF (30 colunas). Alguns registros quebram o split por ";" quando o
 * complemento contém quebra de linha, deslocando UF/município/telefone em +1 posição.
 */
final class RfEstabelecimentoFields {

    private static final Set<String> UFS = Set.of(
        "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG",
        "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    );

    private final String[] cols;
    private final int ufIndex;
    private final String bairro;
    private final String cep;
    private final String phone;

    private RfEstabelecimentoFields(String[] cols, int ufIndex, String bairro, String cep, String phone) {
        this.cols = cols;
        this.ufIndex = ufIndex;
        this.bairro = bairro;
        this.cep = cep;
        this.phone = phone;
    }

    static RfEstabelecimentoFields parse(String[] cols) {
        int ufIdx = detectUfIndex(cols);
        String bairro = RfCsvPaths.field(cols, ufIdx - 2);
        String cep = RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, ufIdx - 1));
        String ddd = RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, ufIdx + 2));
        String tel = RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, ufIdx + 3));
        String phone = ddd.isBlank() || tel.isBlank() ? null : ddd + tel;
        return new RfEstabelecimentoFields(
            cols,
            ufIdx,
            blankToNull(bairro),
            blankToNull(cep),
            phone
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    String uf() {
        return RfCsvPaths.field(cols, ufIndex).toUpperCase(Locale.ROOT);
    }

    String municipioCode() {
        return RfCsvPaths.onlyDigits(RfCsvPaths.field(cols, ufIndex + 1));
    }

    String bairro() {
        return bairro;
    }

    String cep() {
        return cep;
    }

    String phone() {
        return phone;
    }

    private static int detectUfIndex(String[] cols) {
        for (int idx = 19; idx <= 20; idx++) {
            if (isUf(RfCsvPaths.field(cols, idx))) {
                return idx;
            }
        }
        return 19;
    }

    private static boolean isUf(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.length() == 2 && UFS.contains(normalized);
    }
}
