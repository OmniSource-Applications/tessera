package live.omnisource.tessera.datastore.dto;

public record DataStoreRecord(
        DataStoreDto datastore,
        String type,
        String url,
        int poolSize,
        String contactPoints,
        int port,
        String datacenter,
        String keyspace,
        String hosts,
        boolean verifySsl
) {
}
