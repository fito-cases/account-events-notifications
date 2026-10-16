package account;

import java.util.Objects;

public record Customer(String name, String cpf) {
    public Customer {
        Objects.requireNonNull(name);
        Objects.requireNonNull(cpf);
        if (name.isBlank() || cpf.isBlank()) throw new IllegalArgumentException("Customer details required");
    }
}
