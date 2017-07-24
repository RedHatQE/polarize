package com.github.redhatqe.polarize.reporter.importer;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.github.redhatqe.byzantine.utils.IFileHelper;

import com.github.redhatqe.polarize.messagebus.CIBusListener;
import com.github.redhatqe.polarize.messagebus.IMessageListener;
import com.github.redhatqe.polarize.messagebus.MessageResult;
import com.github.redhatqe.polarize.reporter.jaxb.IJAXBHelper;
import com.github.redhatqe.polarize.reporter.jaxb.JAXBHelper;
import org.apache.activemq.memory.list.MessageList;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class ImporterRequest {
    private static Logger logger = LoggerFactory.getLogger(ImporterRequest.class);

    /**
     * Marshalls t of Type T into xml file and uses this generated xml for an importer request
     *
     * @param t The object to be marshalled to xml
     * @param tclass class type to marshall to
     * @param url the URL to send import request to
     * @param xml path for where to store the XML that will be sent to import request
     * @param <T> type of t
     * @return response from sending request
     */
    public static <T> CloseableHttpResponse post(T t, Class<T> tclass, String url, String xml, String user, String pw) {
        JAXBHelper jaxb = new JAXBHelper();
        File importerFile = new File(xml);
        IFileHelper.makeDirs(importerFile.toPath());
        IJAXBHelper.marshaller(t, importerFile, jaxb.getXSDFromResource(tclass));

        return ImporterRequest.post(url, importerFile, user, pw);
    }


    /**
     * Uploads a file to a server through a POST call in multipart_form_data style
     *
     * This style of post is used by the XUnit and Testcase importer, the only difference is the endpoint in the URL.
     * Note that the http response does not hold the data from the request.  Instead, the response of the request is
     * actually sent through the CI Message Bus.  See CIBusListener class
     *
     * @param url
     * @param importerFile
     * @param user
     * @param pw
     * @return
     */
    public static CloseableHttpResponse post(String url, File importerFile , String user, String pw) {
        CloseableHttpResponse response = null;
        if (!importerFile.exists()) {
            logger.error(String.format("%s did not exist", importerFile.toString()));
            return response;
        }
        ImporterRequest.logger.info(String.format("Sending %s to importer:\n", importerFile.toString()));

        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, pw);
        provider.setCredentials(AuthScope.ANY, credentials);

        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultCredentialsProvider(provider)
                .setRedirectStrategy(new LaxRedirectStrategy());

        // FIXME: This should probably go into a helper class since the XUnitReporter is going to need this too
        try {
            URI polarion = null;
            try {
                polarion = new URI(url);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            CloseableHttpClient httpClient;
            if(polarion != null && polarion.getScheme().equals("https")) {
                // setup a Trust Strategy that allows all certificates.
                SSLContext sslContext = null;
                try {
                    sslContext = new SSLContextBuilder().loadTrustMaterial(null, (arg0, arg1) -> true).build();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (KeyManagementException e) {
                    ImporterRequest.logger.error("KeyManagement error");
                    e.printStackTrace();
                } catch (KeyStoreException e) {
                    ImporterRequest.logger.error("KeyStore error");
                    e.printStackTrace();
                }
                builder.setSSLHostnameVerifier(new NoopHostnameVerifier())
                        .setSSLContext(sslContext)
                        .setDefaultCredentialsProvider(provider)
                        .setRedirectStrategy(new LaxRedirectStrategy());
            }

            httpClient = builder.build();
            HttpPost postMethod = new HttpPost(url);

            MultipartEntityBuilder body = MultipartEntityBuilder.create();
            body.addBinaryBody("file", importerFile);
            body.setContentType(ContentType.MULTIPART_FORM_DATA);
            HttpEntity bodyEntity = body.build();
            postMethod.setEntity(bodyEntity);
            response = httpClient.execute(postMethod);
            ImporterRequest.logger.info(response.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        ImporterRequest.printBody(response);
        return response;
    }

    private static void printBody(HttpResponse response) {
        HttpEntity entity = response.getEntity();
        try {
            BufferedReader bfr = new BufferedReader(new InputStreamReader(entity.getContent()));
            System.out.println(bfr.lines().collect(Collectors.joining("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(response.toString());
    }

    private static Optional<File> getBody(HttpResponse response, String path) {
        if (response.getStatusLine().getStatusCode() != 200) {
            logger.error("Status was %s", response.getStatusLine().toString());
            return Optional.empty();
        }

        HttpEntity entity = response.getEntity();
        Path file = null;
        try {
            BufferedReader bfr = new BufferedReader(new InputStreamReader(entity.getContent()));
            String body = bfr.lines().collect(Collectors.joining("\n"));
            System.out.println(body);

            file = Paths.get(path);
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                writer.write(body);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (file == null)
            return Optional.empty();
        else
            return Optional.of(file.toFile());
    }

    /**
     * Makes an importer REST call to upload testrun results
     *
     * @param url url including the server and endpoint
     * @param user user to authenticate as
     * @param pw password for user (note, not encrypted!!)
     * @param reportPath path the XML file that will be uploaded
     * @param selector a JMS Selector string
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws JMSException
     */
    public static Optional<ObjectNode>
    sendImportRequest(CIBusListener cbl,
                      String url,
                      String user,
                      String pw,
                      File reportPath,
                      String selector,
                      String cfgPath)
            throws InterruptedException, ExecutionException, JMSException {
        Supplier<Optional<ObjectNode>> sup = IMessageListener.getCIMessage(cbl, selector, cfgPath);
        CompletableFuture<Optional<ObjectNode>> future = CompletableFuture.supplyAsync(sup);
        // FIXME: While this async code works, it's possible for the calling thread to finish before the handler is
        // called.  Perhaps I can return the future, and the calling thread just joins()?
        //future.thenAccept(messageHandler());

        logger.info("Making import request as user: " + user);
        CloseableHttpResponse resp = ImporterRequest.post(url, reportPath, user, pw);
        HttpEntity entity = resp.getEntity();
        System.out.println(resp.toString());

        // FIXME:  Should I synchronize here?  If I leave this out and return future, it is the caller's responsibility
        // to check by either calling get() or join()
        Optional<ObjectNode> maybeNode = future.get();

        // FIXME: Should I add a message handler here?  or just do it externally?
        return maybeNode;
    }

    public static Optional<MessageResult>
    sendImportRequest(CIBusListener cbl, String selector, String user, String url, String pw, File reportPath) {
        Optional<Connection> conn = cbl.tapIntoMessageBus(selector, cbl.createListener(cbl.messageParser()));

        logger.info("Making import request as user: " + user);
        CloseableHttpResponse resp = ImporterRequest.post(url, reportPath, user, pw);
        System.out.println(resp.toString());

        cbl.listenUntil(1);
        conn.ifPresent(c -> {
            try {
                c.close();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        });
        MessageResult result = cbl.messages.remove();
        logger.info(String.format("The message response status is: %s", result.getStatus().name()));
        return Optional.ofNullable(result);
    }

    /**
     * A Basic get function that can be used to download something from a URL on an https site
     *
     * @param url url of the location
     * @param user user to authenticate as
     * @param pw password for given user
     * @param path path to store body of file
     * @return an Optional File to stored data
     */
    public static Optional<File> get(String url, String user, String pw, String path) {
        CloseableHttpResponse response;
        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, pw);
        provider.setCredentials(AuthScope.ANY, credentials);
        Optional<File> maybeFile = Optional.empty();

        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultCredentialsProvider(provider)
                .setRedirectStrategy(new LaxRedirectStrategy());

        try {
            URI server = null;
            try {
                server = new URI(url);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            CloseableHttpClient httpClient;
            if(server != null && server.getScheme().equals("https")) {
                // setup a Trust Strategy that allows all certificates.
                SSLContext sslContext = null;
                try {
                    sslContext = new SSLContextBuilder().loadTrustMaterial(null, (arg0, arg1) -> true).build();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (KeyManagementException e) {
                    ImporterRequest.logger.error("KeyManagement error");
                    e.printStackTrace();
                } catch (KeyStoreException e) {
                    ImporterRequest.logger.error("KeyStore error");
                    e.printStackTrace();
                }
                builder.setSSLHostnameVerifier(new NoopHostnameVerifier())
                        .setSSLContext(sslContext)
                        .setDefaultCredentialsProvider(provider)
                        .setRedirectStrategy(new LaxRedirectStrategy());
            }

            httpClient = builder.build();
            HttpGet getMethod = new HttpGet(url);
            response = httpClient.execute(getMethod);
            ImporterRequest.logger.info(response.toString());

            maybeFile = ImporterRequest.getBody(response, path);
            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return maybeFile;
    }

    /**
     * A simple method to download a file from a URL (no https, use get for https site)
     *
     * @param url
     * @param output
     * @return
     */
    public static File download(String url, String output) {
        File file = new File(output);
        try {
            URL rUrl = new URL(url);
            ReadableByteChannel rbc = Channels.newChannel(rUrl.openStream());
            FileOutputStream fos = new FileOutputStream(file);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close();
            rbc.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    public static void main(String[] args) {
        // Get a file from jenkins
        String url = args[3];

        String user = args[0];
        String pw = args[1];
        String path = args[2];
        Optional<File> file = ImporterRequest.get(url, user, pw, path);
        if (file.isPresent()) {
            Path fpath = file.get().toPath();
            try {
                BufferedReader bfr = Files.newBufferedReader(fpath);
                String body = bfr.lines().collect(Collectors.joining("\n"));
                System.out.println(body);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
