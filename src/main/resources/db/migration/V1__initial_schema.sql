CREATE TABLE carts (
    id          UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT chk_cart_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CONVERTED'))
);

CREATE INDEX idx_carts_user_status ON carts (user_id, status);
CREATE INDEX idx_carts_expires_at ON carts (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE cart_items (
    id                  UUID          NOT NULL,
    cart_id             UUID          NOT NULL,
    type                VARCHAR(10)   NOT NULL,
    resource_id         UUID          NOT NULL,
    unit_price_amount   NUMERIC(19,2) NOT NULL,
    unit_price_currency VARCHAR(3)    NOT NULL,
    quantity            INT           NOT NULL,
    added_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT fk_ci_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT chk_ci_type CHECK (type IN ('FLIGHT', 'HOTEL')),
    CONSTRAINT chk_ci_quantity CHECK (quantity >= 1)
);

CREATE INDEX idx_ci_cart_id ON cart_items (cart_id);
