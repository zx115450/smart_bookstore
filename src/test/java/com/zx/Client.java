package com.zx;

public class Client {
    private static volatile Client client;

    private Client() {};

    Client getInstance() {
        if (Client.client == null) {
            synchronized (Client.class) {
                if (Client.client == null) {
                    client = new Client();
                }
            }
        }

        return client;
    }

}
