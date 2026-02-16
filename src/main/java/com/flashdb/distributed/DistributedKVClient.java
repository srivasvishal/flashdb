package com.flashdb.distributed;

import com.flashdb.client.KVClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Distributed client that routes requests to the correct server using the Hash Ring.
 * Supports replication: writes to primary + replicas, reads from primary.
 */
public class DistributedKVClient {

    private final HashRing hashRing;
    private final Map<String, KVClient> clients = new HashMap<>();
    private final int replicationFactor;

    public DistributedKVClient(List<String> serverAddresses, int replicationFactor) throws InterruptedException {
        this.hashRing = new HashRing();
        this.replicationFactor = Math.min(replicationFactor, serverAddresses.size());

        for (String addr : serverAddresses) {
            hashRing.addServer(addr);
            String[] parts = addr.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            KVClient client = new KVClient(host, port);
            client.connect();
            clients.put(addr, client);
        }
    }

    public void put(String key, byte[] value) throws Exception {
        List<String> servers = hashRing.getReplicaServers(key, replicationFactor);
        Exception lastError = null;
        for (String server : servers) {
            try {
                clients.get(server).put(key, value);
                if (lastError == null) return;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (lastError != null) throw lastError;
    }

    public byte[] get(String key) throws Exception {
        String server = hashRing.getServer(key);
        if (server == null) throw new IllegalStateException("No servers available");
        return clients.get(server).get(key);
    }

    public void del(String key) throws Exception {
        List<String> servers = hashRing.getReplicaServers(key, replicationFactor);
        for (String server : servers) {
            try {
                clients.get(server).del(key);
            } catch (Exception e) {
                // Continue to other replicas
            }
        }
    }

    public void close() {
        clients.values().forEach(KVClient::close);
    }
}
