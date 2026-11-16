package dev.ledger.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.util.UUID;
import dev.ledger.operation.ConflictException;

@Component
public class AccountClient {
    private final RestClient client;
    public AccountClient(RestClient.Builder builder, @Value("${ledger.accounts-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(2000);
        client = builder.baseUrl(url).requestFactory(factory).build();
    }
    public void requireActive(UUID id, String currency) {
        Account account;
        try {
            account = client.get().uri("/accounts/{id}", id).retrieve().body(Account.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ConflictException("Account does not exist");
        } catch (RestClientException e) {
            throw new AccountUnavailableException();
        }
        if (account == null || !id.equals(account.id())) throw new AccountUnavailableException();
        if (!account.active() || !currency.equals(account.currency()))
            throw new ConflictException("Account is inactive or currency does not match");
    }
    public record Account(UUID id, boolean active, String currency) {}
    public static class AccountUnavailableException extends RuntimeException {
        public AccountUnavailableException() { super("Account service is unavailable"); }
    }
}
