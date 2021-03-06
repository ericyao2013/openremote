package org.openremote.android.service;

import java.io.IOException;

interface TokenCallback {
    void onToken(String accessToken) throws IOException;

    void onFailure(Throwable t);
}
