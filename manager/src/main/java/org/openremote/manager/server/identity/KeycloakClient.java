package org.openremote.manager.server.identity;

import com.google.common.collect.ImmutableList;
import com.hubrick.vertx.rest.RestClientOptions;
import com.hubrick.vertx.rest.RestClientRequest;
import com.hubrick.vertx.rest.converter.HttpMessageConverter;
import com.hubrick.vertx.rest.converter.JacksonJsonHttpMessageConverter;
import com.hubrick.vertx.rest.converter.StringHttpMessageConverter;
import com.hubrick.vertx.rest.impl.DefaultRestClient;
import com.hubrick.vertx.rest.rx.RxRestClient;
import com.hubrick.vertx.rest.rx.impl.DefaultRxRestClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.openremote.manager.server.util.UrlUtil;
import rx.Observable;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import static com.google.common.net.UrlEscapers.urlFormParameterEscaper;
import static com.hubrick.vertx.rest.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static com.hubrick.vertx.rest.MediaType.APPLICATION_JSON_VALUE;
import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class KeycloakClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(KeycloakClient.class.getName());

    public static final String ADMIN_CLI_CLIENT = "admin-cli";
    public static final String CONTEXT_PATH = "auth";

    final protected List<HttpMessageConverter> messageConverters = ImmutableList.of(
        new StringHttpMessageConverter(),
        new JacksonJsonHttpMessageConverter<>(Json.mapper)
    );

    final protected DefaultRestClient defaultClient;
    final protected RxRestClient client;

    public KeycloakClient(Vertx vertx, RestClientOptions restClientOptions) {
        defaultClient = new DefaultRestClient(vertx, restClientOptions, messageConverters);
        client = new DefaultRxRestClient(defaultClient);
    }

    @Override
    public void close() {
        defaultClient.close();
    }

    public Observable<AccessTokenResponse> authenticateDirectly(String realm, String clientId, String username, String password) {
        return client.post(
            UrlUtil.getPath(CONTEXT_PATH, "realms", realm, "protocol", "openid-connect", "token"),
            AccessTokenResponse.class,
            request -> {
                request.putHeader(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_VALUE);
                request.end(
                    "client_id=" + urlFormParameterEscaper().escape(clientId) +
                        "&grant_type=" + "password" +
                        "&username=" + urlFormParameterEscaper().escape(username) +
                        "&password=" + urlFormParameterEscaper().escape(password)
                );
            }
        ).flatMap(response -> Observable.just(response.getBody()));
    }

    public Observable<String> registerClientApplication(String realm, ClientRepresentation clientRepresentation) {
        return client.post(
            UrlUtil.getPath(CONTEXT_PATH, "admin", "realms", realm, "clients"),
            request -> {
                if (clientRepresentation.getRegistrationAccessToken() == null) {
                    throw new IllegalStateException("Missing registration access token on client representation");
                }
                addBearerAuthorization(request, clientRepresentation.getRegistrationAccessToken());
                request.putHeader(ACCEPT, APPLICATION_JSON_VALUE);
                request.putHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
                request.end(clientRepresentation);
            }
        ).flatMap(response -> Observable.just(response.headers().get(HttpHeaders.LOCATION)));
    }

    public Observable<ClientRepresentation> getClientApplications(String realm, String accessToken) {
        return client.get(
            UrlUtil.getPath(CONTEXT_PATH, "admin", "realms", realm, "clients"),
            ClientRepresentation[].class,
            request -> {
                addBearerAuthorization(request, accessToken);
                request.putHeader(ACCEPT, APPLICATION_JSON_VALUE);
                request.end();
            }
        ).flatMap(response -> Observable.from(response.getBody()));
    }

    public Observable<ClientRepresentation> getClientApplicationByLocation(String realm, String accessToken, String location) {
        return client.get(
            location,
            ClientRepresentation.class,
            request -> {
                addBearerAuthorization(request, accessToken);
                request.putHeader(ACCEPT, APPLICATION_JSON_VALUE);
                request.end();
            }
        ).flatMap(response -> Observable.just(response.getBody()));
    }

    public Observable<ClientRepresentation> getClientApplication(String realm, String accessToken, String clientId) {
        return client.get(
            UrlUtil.getPath(CONTEXT_PATH, "admin", "realms", realm, "clients", clientId),
            ClientRepresentation.class,
            request -> {
                addBearerAuthorization(request, accessToken);
                request.putHeader(ACCEPT, APPLICATION_JSON_VALUE);
                request.end();
            }
        ).flatMap(response -> Observable.just(response.getBody()));
    }

    public Observable<Integer> deleteClientApplication(String realm, String accessToken, String clientId) {
        return client.delete(
            UrlUtil.getPath(CONTEXT_PATH, "admin", "realms", realm, "clients", clientId),
            request -> {
                addBearerAuthorization(request, accessToken);
                request.end();
            }
        ).flatMap(response -> Observable.just(response.statusCode()));
    }

    public Observable<ClientInstall> getClientInstall(String realm, String clientId) {
        return getClientInstall(realm, clientId, null);
    }

    public Observable<ClientInstall> getClientInstall(String realm, String clientId, String clientSecret) {
        return client.get(
            UrlUtil.getPath(CONTEXT_PATH, "realms", realm, "clients", "install", clientId),
            ClientInstall.class,
            request -> {
                if (clientSecret != null) {
                    addClientAuthorization(request, clientId, clientSecret);
                }
                request.putHeader(ACCEPT, APPLICATION_JSON_VALUE);
                request.end();
            }
        ).flatMap(response -> Observable.just(response.getBody()));
    }


    protected void addBearerAuthorization(RestClientRequest request, String accessToken) {
        String authorization = "Bearer " + accessToken;
        request.putHeader(HttpHeaders.AUTHORIZATION, authorization);
    }

    protected void addClientAuthorization(RestClientRequest request, String clientId, String clientSecret) {
        try {
            String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes("utf-8")
            );
            request.putHeader(HttpHeaders.AUTHORIZATION, authorization);
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }
}