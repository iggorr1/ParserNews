package com.parsernews.service;

import java.io.IOException;

public interface SecCurrentFilingsClient {
    String fetchCurrentFilingsAtom(String form, int count) throws IOException, InterruptedException;
}
