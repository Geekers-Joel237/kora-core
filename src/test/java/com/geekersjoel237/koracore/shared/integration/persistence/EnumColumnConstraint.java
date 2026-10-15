package com.geekersjoel237.koracore.shared.integration.persistence;

import jakarta.persistence.EntityManager;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public final class EnumColumnConstraint {

    /**
     * PostgreSQL does not store the text we wrote. {@code col IN ('A', 'B')} comes back
     * normalised as {@code ((col)::text = ANY ((ARRAY['A'::character varying,
     * 'B'::character varying])::text[]))}. The literals survive that rewrite unchanged,
     * and they are the only single-quoted runs in the definition.
     */
    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

    private EnumColumnConstraint() {
    }

    public static Set<String> allowedValues(EntityManager em, String tableName, String constraintName) {
        @SuppressWarnings("unchecked")
        List<String> definitions = em.createNativeQuery("""
                        SELECT pg_get_constraintdef(c.oid)
                        FROM pg_constraint c
                        JOIN pg_class t ON t.oid = c.conrelid
                        WHERE c.contype = 'c'
                          AND t.relname = :table
                          AND c.conname = :constraint
                        """)
                .setParameter("table", tableName)
                .setParameter("constraint", constraintName)
                .getResultList();

        assertThat(definitions)
                .describedAs("CHECK constraint %s on %s — add it in a migration", constraintName, tableName)
                .hasSize(1);

        return literalsOf(definitions.getFirst());
    }

    public static Set<String> namesOf(Enum<?>[] constants) {
        return Arrays.stream(constants)
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static Set<String> literalsOf(String constraintDefinition) {
        Set<String> literals = new TreeSet<>();
        Matcher matcher = LITERAL.matcher(constraintDefinition);
        while (matcher.find()) {
            literals.add(matcher.group(1));
        }
        return literals;
    }
}
