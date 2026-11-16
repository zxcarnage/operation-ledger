package dev.ledger.operation;
public class NotFoundException extends RuntimeException {
    public NotFoundException() { super("Operation not found"); }
}
