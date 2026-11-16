package dev.ledger.operation;
import java.util.Set;
public enum Status {
    NEW, PROCESSING, COMPLETED, FAILED, CANCELLED;
    public Set<Status> next() {
        return switch (this) {
            case NEW -> Set.of(PROCESSING, CANCELLED);
            case PROCESSING -> Set.of(COMPLETED, FAILED);
            default -> Set.of();
        };
    }
    public void requireTransition(Status target) {
        if (!next().contains(target)) throw new ConflictException("Transition " + this + " -> " + target + " is forbidden");
    }
}
