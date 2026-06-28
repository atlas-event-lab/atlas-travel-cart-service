package com.atlas.cart.shared.exception;

import java.net.URI;

public final class ProblemTypes {

    public static final URI VALIDATION       = URI.create("https://atlas/errors/validation");
    public static final URI NOT_FOUND        = URI.create("https://atlas/errors/not-found");
    public static final URI FORBIDDEN        = URI.create("https://atlas/errors/forbidden");
    public static final URI CONFLICT         = URI.create("https://atlas/errors/conflict");
    public static final URI GONE             = URI.create("https://atlas/errors/gone");
    public static final URI UNPROCESSABLE    = URI.create("https://atlas/errors/unprocessable-entity");
    public static final URI INTERNAL_ERROR   = URI.create("https://atlas/errors/internal-server-error");

    private ProblemTypes() {}
}
