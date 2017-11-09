package com.github.redhatqe.polarize.messagebus;

import com.github.redhatqe.byzantine.configuration.Serializer;
import com.github.redhatqe.polarize.configuration.Broker;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Optional;

/**
 *
 */
public interface ICIBus {
    Logger logger = LogManager.getLogger("byzantine-" + ICIBus.class.getName());

    static String getDefaultConfigPath() {
        String home = System.getProperty("user.home");
        return FileSystems.getDefault().getPath(home, "/.polarize/broker-config.yaml").toString();
    }

    static public <T> Optional<T> getConfigFromPath(Class<T> cfg, String path) {
        T config = null;
        try {
            if(path.endsWith(".json"))
                config = Serializer.fromJson(cfg, new File(path));
            else if (path.endsWith(".yaml") || path.endsWith(".yml"))
                config = Serializer.fromYaml(cfg, new File(path));
            else
                logger.error("Unknown configuration file type");
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return Optional.ofNullable(config);
    }


    default void authByPassword(ActiveMQConnectionFactory factory, Broker broker) {
        String user = broker.getUser();
        String pw = broker.getPassword();
        factory.setUserName(user);
        factory.setPassword(pw);
    }

    default void authByKeys(ActiveMQSslConnectionFactory factory, Broker broker) {
        // Path to the jks
        try {
            factory.setKeyStore(String.format("file:///%s", broker.getKeystorePath()));
            // Password of the private key
            factory.setKeyStoreKeyPassword(broker.getKeystoreKeyPassword());
            // password of the keystore
            factory.setKeyStorePassword(broker.getKeystorePassword());
            // set the truststore jks file
            factory.setTrustStore(String.format("file:///%s", broker.getTruststorePath()));
            // set the truststore password
            factory.setTrustStorePassword(broker.getTruststorePassword());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    default ActiveMQConnectionFactory setupFactory(String url, Broker broker) {
        if(url.contains("ssl:")) {
            ActiveMQSslConnectionFactory factory = new ActiveMQSslConnectionFactory(url);
            this.authByKeys(factory, broker);
            return factory;
        }
        else {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
            this.authByPassword(factory, broker);
            return factory;
        }
    }
}
