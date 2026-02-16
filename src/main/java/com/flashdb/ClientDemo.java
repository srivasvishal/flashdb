package com.flashdb;

import com.flashdb.client.KVClient;

/**
 * Client SDK demo: basic put/get/del operations.
 * Run the server first: mvn exec:java
 *
 * For real-world demo with open source data: mvn exec:java -Preal-data
 */
public class ClientDemo {

    public static void main(String[] args) throws Exception {
        KVClient client = new KVClient("localhost", 8080);
        client.connect();

        client.put("hello", "world".getBytes());
        System.out.println("GET hello: " + new String(client.get("hello")));

        client.put("foo", "bar".getBytes());
        System.out.println("GET foo: " + new String(client.get("foo")));

        client.del("foo");
        System.out.println("GET foo after del: " + client.get("foo"));

        client.close();
        System.out.println("Demo complete.");
    }
}
