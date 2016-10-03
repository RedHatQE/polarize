package com.github.redhatqe.polarize.importer;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarize.CIBusListener;
import com.github.redhatqe.polarize.IFileHelper;
import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.JAXBHelper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by stoner on 8/31/16.
 */
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
        try {
            String text = Files.lines(importerFile.toPath()).reduce("", (acc, c) -> acc + c + "\n");
            ImporterRequest.logger.info(String.format("Sending this file to importer:\n%s", text));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CloseableHttpResponse response = null;
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
    sendImportRequest(String url, String user, String pw, File reportPath, String selector,
                      Consumer<Optional<ObjectNode>> handler)
            throws InterruptedException, ExecutionException, JMSException {
        Supplier<Optional<ObjectNode>> sup = CIBusListener.getCIMessage(selector);
        CompletableFuture<Optional<ObjectNode>> future = CompletableFuture.supplyAsync(sup);
        // FIXME: While this async code works, it's possible for the calling thread to finish before the handler is
        // called.  Perhaps I can return the future, and the calling thread just joins()?
        //future.thenAccept(messageHandler());

        CloseableHttpResponse resp = ImporterRequest.post(url, reportPath, user, pw);
        HttpEntity entity = resp.getEntity();
        try {
            BufferedReader bfr = new BufferedReader(new InputStreamReader(entity.getContent()));
            System.out.println(bfr.lines().collect(Collectors.joining("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(resp.toString());

        // FIXME:  Should I synchronize here?  If I leave this out and return future, it is the caller's responsibility
        // to check by either calling get() or join()
        Optional<ObjectNode> maybeNode = future.get();

        // FIXME: Should I add a message handler here?  or just do it externally?
        handler.accept(maybeNode);
        return maybeNode;
    }
}