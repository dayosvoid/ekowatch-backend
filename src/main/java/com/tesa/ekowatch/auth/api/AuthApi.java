package com.tesa.ekowatch.auth.api;

public interface AuthApi {
    SessionView createSession(CreateSessionRequest request);
}
