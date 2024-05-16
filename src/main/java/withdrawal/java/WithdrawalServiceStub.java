package withdrawal.java;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import static withdrawal.java.WithdrawalService.WithdrawalState.COMPLETED;
import static withdrawal.java.WithdrawalService.WithdrawalState.FAILED;
import static withdrawal.java.WithdrawalService.WithdrawalState.PROCESSING;


public class WithdrawalServiceStub implements WithdrawalService {
    private final ConcurrentMap<WithdrawalService.WithdrawalId, Withdrawal> requests = new ConcurrentHashMap<>();

    @Override
    public void requestWithdrawal(WithdrawalId id, Address address, Integer amount) { // Please substitute T with prefered type
        final var existing = requests.putIfAbsent(id, new Withdrawal(finalState(), finaliseAt(), address, amount));
        if (existing != null && !Objects.equals(existing.address, address) && !Objects.equals(existing.amount, amount))
            throw new IllegalStateException("Withdrawal request with id[%s] is already present".formatted(id));
    }

    private WithdrawalState finalState() {
        return ThreadLocalRandom.current().nextBoolean() ? COMPLETED : FAILED;
    }

    private long finaliseAt() {
        return System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(1000, 10000);
    }

    @Override
    public WithdrawalState getRequestState(WithdrawalId id) {
        final var request = requests.get(id);
        if (request == null)
            throw new IllegalArgumentException("Request %s is not found".formatted(id));
        return request.finalState();
    }

    record Withdrawal(WithdrawalState state, long finaliseAt, Address address, Integer amount) {
        public WithdrawalState finalState() {
            return finaliseAt <= System.currentTimeMillis() ? state : PROCESSING;
        }
    }
}
