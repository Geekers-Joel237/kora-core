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

/**
 * Reads back the value list a {@code CHECK} constraint actually enforces, straight
 * from the PostgreSQL catalog.
 *
 * <p>A migration that adds {@code CHECK (state IN (...))} is only worth what the
 * database made of it. This asks {@code pg_get_constraintdef} what is in force on the
 * running schema, so a module test can assert that the set the column accepts
 * <em>equals</em> the set the Java side defines — a new enum constant shipped without
 * a migration then fails the build instead of failing an insert in production.
 *
 * <p>Kernel code: it names a constraint and a table, never a module.
 */
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

    /**
     * @return every value {@code constraintName} on {@code tableName} lets through.
     */
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

    /** The names of a Java enum's constants, as they reach the column through {@code @Enumerated(STRING)}. */
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
