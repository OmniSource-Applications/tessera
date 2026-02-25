package live.omnisource.tessera.config;

import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.filestore.crypto.KeyProvider;
import live.omnisource.tessera.filestore.crypto.SecureFileStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration
public class SecureFileStoreConfig {

    @Bean
    public SecureFileStore secureFileStore(FileStoreService fileStoreService,
                                           ApplicationProperties properties) {
        var keyProvider = KeyProvider.fromProperties(properties.crypto());
        return new SecureFileStore(
                fileStoreService.secretsDir(),
                new SecureRandom(),
                keyProvider.getKey(),
                fileStoreService
        );
    }
}
