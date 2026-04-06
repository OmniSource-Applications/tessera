package live.omnisource.tessera.config;

import java.security.SecureRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import live.omnisource.tessera.filestore.FileStoreService;
import live.omnisource.tessera.filestore.crypto.KeyProvider;
import live.omnisource.tessera.filestore.crypto.SecureFileStore;

@Configuration
public class SecureFileStoreConfig {

  @Bean
  public SecureFileStore secureFileStore(
      FileStoreService fileStoreService, ApplicationProperties properties) {
    var keyProvider = KeyProvider.fromProperties(properties.crypto());
    return new SecureFileStore(
        fileStoreService.secretsDir(), new SecureRandom(), keyProvider.getKey(), fileStoreService);
  }
}
