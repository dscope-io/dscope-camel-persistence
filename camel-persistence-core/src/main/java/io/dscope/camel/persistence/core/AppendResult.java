package io.dscope.camel.persistence.core;

public record AppendResult(
    long previousVersion,
    long nextVersion,
    boolean duplicate
) {
}
