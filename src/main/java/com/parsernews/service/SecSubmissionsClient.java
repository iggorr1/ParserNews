package com.parsernews.service;

import java.io.IOException;

public interface SecSubmissionsClient {
    String fetchSubmissionsJson(String paddedCik) throws IOException, InterruptedException;
}
