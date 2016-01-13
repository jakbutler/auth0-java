/*
 * AuthenticationAPIClient.java
 *
 * Copyright (c) 2015 Auth0 (http://auth0.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.auth0.authentication;

import com.auth0.authentication.api.ParameterBuilder;
import com.auth0.authentication.api.ParameterizableRequest;
import com.auth0.authentication.api.Request;
import com.auth0.authentication.api.internal.RequestFactory;
import com.auth0.Application;
import com.auth0.Auth0;
import com.auth0.DatabaseUser;
import com.auth0.Token;
import com.auth0.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;

import java.util.Map;

import static com.auth0.authentication.api.ParameterBuilder.GRANT_TYPE_PASSWORD;

/**
 * API client for Auth0 Authentication API.
 * @see <a href="https://auth0.com/docs/auth-api">Auth API docs</a>
 */
public class AuthenticationAPIClient {

    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";
    private static final String DEFAULT_DB_CONNECTION = "Username-Password-Authentication";
    private static final String ID_TOKEN_KEY = "id_token";
    private static final String EMAIL_KEY = "email";
    private static final String REFRESH_TOKEN_KEY = "refresh_token";

    private static final String PHONE_NUMBER_KEY = "phone_number";

    private final Auth0 auth0;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    private String defaultDbConnection = DEFAULT_DB_CONNECTION;

    /**
     * Creates a new API client instance providing Auth0 account info.
     * @param auth0 account information
     */
    public AuthenticationAPIClient(Auth0 auth0) {
        this(auth0, new OkHttpClient(), new ObjectMapper());
    }

    /**
     * Creates a new API client instance providing Auth API and Configuration Urls different than the default. (Useful for on premise deploys).
     * @param clientID Your application clientID.
     * @param baseURL Auth0's auth API endpoint
     * @param configurationURL Auth0's enpoint where App info can be retrieved.
     */
    @SuppressWarnings("unused")
    public AuthenticationAPIClient(String clientID, String baseURL, String configurationURL) {
        this(new Auth0(clientID, baseURL, configurationURL));
    }

    protected AuthenticationAPIClient(Auth0 auth0, OkHttpClient client, ObjectMapper mapper) {
        this.auth0 = auth0;
        this.client = client;
        this.mapper = mapper;
    }

    public String getClientId() {
        return auth0.getClientId();
    }

    public String getBaseURL() {
        return auth0.getDomainUrl();
    }

    /**
     * Set the default DB connection name used. By default is 'Username-Password-Authentication'
     * @param defaultDbConnection name to use on every login with DB connection
     */
    public void setDefaultDbConnection(String defaultDbConnection) {
        this.defaultDbConnection = defaultDbConnection;
    }

    /**
     * Fetch application information from Auth0
     * @return a Auth0 request to start
     */
    public Request<Application> fetchApplicationInfo() {
        HttpUrl url = HttpUrl.parse(auth0.getConfigurationUrl()).newBuilder()
                .addPathSegment("client")
                .addPathSegment(auth0.getClientId() + ".js")
                .build();
        return RequestFactory.newApplicationInfoRequest(url, client, mapper);
    }

    /**
     * Log in a user with email/username and password using a DB connection and fetch it's profile from Auth0
     * @param usernameOrEmail of the user depending of the type of DB connection
     * @param password of the user
     * @return a request to configure and start that will yield {@link Token} and {@link UserProfile}
     */
    public AuthenticationRequest login(String usernameOrEmail, String password) {
        Map<String, Object> requestParameters = new ParameterBuilder()
                .set(USERNAME_KEY, usernameOrEmail)
                .set(PASSWORD_KEY, password)
                .setGrantType(GRANT_TYPE_PASSWORD)
                .asDictionary();
        return newAuthenticationRequest(requestParameters);
    }

    /**
     * Log in a user with a OAuth 'access_token' of a Identity Provider like Facebook or Twitter using <a href="https://auth0.com/docs/auth-api#!#post--oauth-access_token">'\oauth\access_token' endpoint</a>
     * @param token obtained from the IdP
     * @param connection that will be used to authenticate the user, e.g. 'facebook'
     * @return a request to configure and start that will yield {@link Token} and {@link UserProfile}
     */
    public AuthenticationRequest loginWithOAuthAccessToken(String token, String connection) {
        HttpUrl url = HttpUrl.parse(auth0.getDomainUrl()).newBuilder()
                .addPathSegment("oauth")
                .addPathSegment("access_token")
                .build();

        Map<String, Object> parameters = ParameterBuilder.newBuilder()
                .setClientId(getClientId())
                .setConnection(connection)
                .setAccessToken(token)
                .asDictionary();

        final ParameterizableRequest<UserProfile> profileRequest = profileRequest();
        ParameterizableRequest<Token> credentialsRequest = RequestFactory.POST(url, client, mapper, Token.class)
                .addParameters(parameters);
        return new AuthenticationRequest(credentialsRequest, profileRequest);
    }

    /**
     * Log in a user using a phone number and a verification code received via SMS (Part of passwordless login flow)
     * @param phoneNumber where the user received the verification code
     * @param verificationCode sent by Auth0 via SMS
     * @return a request to configure and start that will yield {@link Token} and {@link UserProfile}
     */
    public AuthenticationRequest loginWithPhoneNumber(String phoneNumber, String verificationCode) {
        Map<String, Object> parameters = ParameterBuilder.newBuilder()
                .set(USERNAME_KEY, phoneNumber)
                .set(PASSWORD_KEY, verificationCode)
                .setGrantType(GRANT_TYPE_PASSWORD)
                .setClientId(getClientId())
                .setConnection("sms")
                .asDictionary();
        return newAuthenticationRequest(parameters);
    }

    /**
     * Log in a user using an email and a verification code received via Email (Part of passwordless login flow)
     * @param email where the user received the verification code
     * @param verificationCode sent by Auth0 via Email
     * @return a request to configure and start that will yield {@link Token} and {@link UserProfile}
     */
    public AuthenticationRequest loginWithEmail(String email, String verificationCode) {
        Map<String, Object> parameters = ParameterBuilder.newBuilder()
                .set(USERNAME_KEY, email)
                .set(PASSWORD_KEY, verificationCode)
                .setGrantType(GRANT_TYPE_PASSWORD)
                .setClientId(getClientId())
                .setConnection("email")
                .asDictionary();
        return newAuthenticationRequest(parameters);
    }

    /**
     * Fetch the token information from Auth0
     * @param idToken used to fetch it's information
     * @return a request to start
     */
    public Request<UserProfile> tokenInfo(String idToken) {
        Map<String, Object> requestParameters = new ParameterBuilder()
                .clearAll()
                .set(ID_TOKEN_KEY, idToken)
                .asDictionary();
        return profileRequest()
                .addParameters(requestParameters);
    }

    /**
     * Creates a user in a DB connection using <a href="https://auth0.com/docs/auth-api#!#post--dbconnections-signup">'/dbconnections/signup' endpoint</a>
     * @param email of the user and must be non null
     * @param password of the user and must be non null
     * @param username of the user and must be non null
     * @return a request to start
     */
    public ParameterizableRequest<DatabaseUser> createUser(String email, String password, String username) {
        HttpUrl url = HttpUrl.parse(auth0.getDomainUrl()).newBuilder()
                .addPathSegment("dbconnections")
                .addPathSegment("signup")
                .build();
        Map<String, Object> parameters = new ParameterBuilder()
                .set(USERNAME_KEY, username)
                .set(EMAIL_KEY, email)
                .set(PASSWORD_KEY, password)
                .setConnection(defaultDbConnection)
                .setClientId(getClientId())
                .asDictionary();
        return RequestFactory.POST(url, client, mapper, DatabaseUser.class)
                .addParameters(parameters);
    }

    /**
     * Creates a user in a DB connection using <a href="https://auth0.com/docs/auth-api#!#post--dbconnections-signup">'/dbconnections/signup' endpoint</a>
     * @param email of the user and must be non null
     * @param password of the user and must be non null
     * @return a request to start
     */
    public ParameterizableRequest<DatabaseUser> createUser(String email, String password) {
        return createUser(email, password, null);
    }

    /**
     * Creates a user in a DB connection using <a href="https://auth0.com/docs/auth-api#!#post--dbconnections-signup">'/dbconnections/signup' endpoint</a>
     * and then logs in and fetches it's user profile
     * @param email of the user and must be non null
     * @param password of the user and must be non null
     * @param username of the user and must be non null
     * @return a request to configure and start that will yield {@link Token} and {@link UserProfile}
     */
    public SignUpRequest signUp(String email, String password, String username) {
        ParameterizableRequest<DatabaseUser> createUserRequest = createUser(email, password, username);
        AuthenticationRequest authenticationRequest = login(email, password);
        return new SignUpRequest(createUserRequest, authenticationRequest);
    }

    /**
     * Creates a user in a DB connection using <a href="https://auth0.com/docs/auth-api#!#post--dbconnections-signup">'/dbconnections/signup' endpoint</a>
     * and then logs in and fetches it's user profile
     * @param email of the user and must be non null
     * @param password of the user and must be non null
     * @return a request to configure and start that will yield {@link Token} and {@link UserProfile}
     */
    public SignUpRequest signUp(String email, String password) {
        ParameterizableRequest<DatabaseUser> createUserRequest = createUser(email, password);
        AuthenticationRequest authenticationRequest = login(email, password);
        return new SignUpRequest(createUserRequest, authenticationRequest);
    }

    /**
     * Perform a change password request using <a href="https://auth0.com/docs/auth-api#!#post--dbconnections-change_password">'/dbconnections/change_password'</a>
     * @param email of the user that changes the password. It's also where the confirmation email will be sent
     * @return a request to configure and start
     */
    public ChangePasswordRequest changePassword(String email) {
        HttpUrl url = HttpUrl.parse(auth0.getDomainUrl()).newBuilder()
                .addPathSegment("dbconnections")
                .addPathSegment("change_password")
                .build();

        Map<String, Object> parameters = ParameterBuilder.newBuilder()
                .set(EMAIL_KEY, email)
                .setClientId(getClientId())
                .setConnection(defaultDbConnection)
                .asDictionary();

        ParameterizableRequest<Void> request = RequestFactory.POST(url, client, mapper)
                .addParameters(parameters);
        return new ChangePasswordRequest(request);
    }

    /**
     * Performs a <a href="https://auth0.com/docs/auth-api#!#post--delegation">delegation</a> request that will yield a new Auth0 'id_token'
     * @param idToken issued by Auth0 for the user. The token must not be expired.
     * @return a request to configure and start
     */
    public DelegationRequest delegationWithIdToken(String idToken) {
        Map<String, Object> parameters = new ParameterBuilder()
                .clearAll()
                .set(ID_TOKEN_KEY, idToken)
                .asDictionary();
        ParameterizableRequest<Map<String, Object>> request = delegation()
                .addParameters(parameters);
        return new DelegationRequest(request);
    }

    /**
     * Performs a <a href="https://auth0.com/docs/auth-api#!#post--delegation">delegation</a> request that will yield a new Auth0 'id_token'.
     * Check our <a href="https://auth0.com/docs/refresh-token">refresh token</a> docs for more information
     * @param refreshToken issued by Auth0 for the user when using the 'offline_access' scope when logging in.
     * @return a request to configure and start
     */
    public DelegationRequest delegationWithRefreshToken(String refreshToken) {
        Map<String, Object> parameters = new ParameterBuilder()
                .clearAll()
                .set(REFRESH_TOKEN_KEY, refreshToken)
                .asDictionary();
        ParameterizableRequest<Map<String, Object>> request = delegation()
                .addParameters(parameters);
        return new DelegationRequest(request);
    }

    /**
     * Unlink a user identity calling <a href="https://auth0.com/docs/auth-api#!#post--unlink">'/unlink'</a> endpoint
     * @param userId of the identity to unlink
     * @param accessToken of the main identity obtained after login
     * @return a request to start
     */
    public Request<Void> unlink(String userId, String accessToken) {
        Map<String, Object> parameters = new ParameterBuilder()
                .clearAll()
                .set("clientID", getClientId())
                .set("user_id", userId)
                .set("access_token", accessToken)
                .asDictionary();
        HttpUrl url = HttpUrl.parse(auth0.getDomainUrl()).newBuilder()
                .addPathSegment("unlink")
                .build();
        return RequestFactory.POST(url, client, mapper)
                .addParameters(parameters);
    }

    /**
     * Start a passwordless flow with <a href="https://auth0.com/docs/auth-api#!#post--with_email">Email</a>
     * @param email that will receive a verification code to use for login
     * @param passwordlessType indicate whether the email should contain a code, link or magic link (android & iOS)
     * @return a request to configure and start
     */
    public ParameterizableRequest<Void> passwordlessWithEmail(String email, PasswordlessType passwordlessType) {
        Map<String, Object> parameters = ParameterBuilder.newBuilder()
                .clearAll()
                .set(EMAIL_KEY, email)
                .setSend(passwordlessType)
                .setConnection("email")
                .asDictionary();
        return passwordless()
                .addParameters(parameters);
    }

    /**
     * Start a passwordless flow with <a href="https://auth0.com/docs/auth-api#!#post--with_sms">SMS</a>
     * @param phoneNumber where an SMS with a verification code will be sent
     * @param passwordlessType indicate whether the SMS should contain a code, link or magic link (android & iOS)
     * @return a request to configure and start
     */
    public ParameterizableRequest<Void> passwordlessWithSMS(String phoneNumber, PasswordlessType passwordlessType) {
        Map<String, Object> parameters = ParameterBuilder.newBuilder()
                .clearAll()
                .set(PHONE_NUMBER_KEY, phoneNumber)
                .setSend(passwordlessType)
                .setConnection("sms")
                .asDictionary();
        return passwordless()
                .addParameters(parameters);
    }

    protected ParameterizableRequest<Map<String, Object>> delegation() {
        HttpUrl url = HttpUrl.parse(auth0.getDomainUrl()).newBuilder()
                .addPathSegment("delegation")
                .build();
        Map<String, Object> parameters = ParameterBuilder.newEmptyBuilder()
                .clearAll()
                .setClientId(getClientId())
                .setGrantType(ParameterBuilder.GRANT_TYPE_JWT)
                .asDictionary();
        return RequestFactory.rawPOST(url, client, mapper)
                .addParameters(parameters);
    }

    protected ParameterizableRequest<Void> passwordless() {
        HttpUrl url = HttpUrl.parse(auth0.getDomainUrl()).newBuilder()
                .addPathSegment("passwordless")
                .addPathSegment("start")
                .build();

        Map<String, Object> parameters = ParameterBuilder.newBuilder()
                .clearAll()
                .setClientId(getClientId())
                .asDictionary();

        return RequestFactory.POST(url, client, mapper)
                .addParameters(parameters);
    }

    protected ParameterizableRequest<Token> loginWithResourceOwner() {
        HttpUrl url = HttpUrl.parse(auth0.getDomainUrl()).newBuilder()
                .addPathSegment("oauth")
                .addPathSegment("ro")
                .build();

        Map<String, Object> requestParameters = new ParameterBuilder()
                .setClientId(getClientId())
                .setConnection(defaultDbConnection)
                .asDictionary();
        ParameterizableRequest<Token> request = RequestFactory.POST(url, client, mapper, Token.class)
                .addParameters(requestParameters);
        return request;
    }

    private ParameterizableRequest<UserProfile> profileRequest() {
        HttpUrl url = HttpUrl.parse(auth0.getDomainUrl()).newBuilder()
                .addPathSegment("tokeninfo")
                .build();
        return RequestFactory.POST(url, client, mapper, UserProfile.class);

    }

    private AuthenticationRequest newAuthenticationRequest(Map<String, Object> parameters) {
        final ParameterizableRequest<Token> credentialsRequest = loginWithResourceOwner()
                .addParameters(parameters);
        final ParameterizableRequest<UserProfile> profileRequest = profileRequest();

        return new AuthenticationRequest(credentialsRequest, profileRequest);
    }
}
