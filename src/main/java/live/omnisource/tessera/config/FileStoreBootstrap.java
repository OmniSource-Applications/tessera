package live.omnisource.tessera.config;

import live.omnisource.tessera.filestore.FileStoreService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStoreBootstrap {
    @Bean
    public ApplicationRunner fileStoreBootstrapper(FileStoreService fileStoreService){
        return _ ->
                fileStoreService.init();
    }
}
