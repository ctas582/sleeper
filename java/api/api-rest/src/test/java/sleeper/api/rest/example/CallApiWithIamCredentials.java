/*
 * Copyright 2022-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sleeper.api.rest.example;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.utils.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;

import static sleeper.core.properties.instance.CdkDefinedInstanceProperty.REST_API_IAM_URL;

/**
 * An example showing how to call the Sleeper REST API using IAM role credentials.
 */
public class CallApiWithIamCredentials {

    private static final String SERVICE = "execute-api";

    private CallApiWithIamCredentials() {
    }

    /**
     * Calls the API and reports the response.
     *
     * @param  args      the URL to call, and optionally the region to sign for
     * @throws Exception if the request could not be made
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Usage: <url> [region]. The URL is the IAM endpoint of a deployed instance, which is the value "
                            + "of " + REST_API_IAM_URL.getPropertyName() + ", followed by the path to call.");
        }
        URI url = URI.create(args[0]);
        Region region = args.length > 1 ? Region.of(args[1]) : new DefaultAwsRegionProviderChain().getRegion();

        try (SdkHttpClient httpClient = UrlConnectionHttpClient.create()) {
            HttpExecuteResponse response = call(httpClient, url, region);
            report(response);
        }
    }

    /**
     * Signs and executes a request with AWS credentials.
     *
     * @param  httpClient  the client to send it with
     * @param  url         the URL to call
     * @param  region      the region to sign for, which must match where the API is deployed
     * @return             the response
     * @throws IOException if the request could not be sent
     */
    public static HttpExecuteResponse call(SdkHttpClient httpClient, URI url, Region region) throws IOException {
        SignedRequest signed;
        try (DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build()) {
            AwsCredentials credentials = credentialsProvider.resolveCredentials();
            signed = AwsV4HttpSigner.create().sign(request -> request
                    .identity(credentials)
                    .request(SdkHttpRequest.builder()
                            .method(SdkHttpMethod.GET)
                            .uri(url)
                            .build())
                    .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, SERVICE)
                    .putProperty(AwsV4HttpSigner.REGION_NAME, region.id()));
        }

        return httpClient.prepareRequest(HttpExecuteRequest.builder()
                .request(signed.request())
                .build())
                .call();
    }

    /**
     * Reports what the API returned.
     *
     * @param  response    the response
     * @throws IOException if the response could not be read
     */
    private static void report(HttpExecuteResponse response) throws IOException {
        int status = response.httpResponse().statusCode();
        String body = readBody(response);
        System.out.println(status + " " + response.httpResponse().statusText().orElse(""));
        switch (status) {
            case 403 -> System.out.println(
                    "API Gateway refused the request. The role is not permitted to invoke the API, or the request was signed for the wrong region.");
            case 401 -> System.out.println(
                    "The request reached the Sleeper REST API but carried no caller. Check the URL is the IAM endpoint, which is "
                            + REST_API_IAM_URL.getPropertyName() + ", rather than the endpoint that expects a token.");
            case 503 -> System.out.println(
                    "The instance has no way for a client to authenticate. Call /api/v1/auth/info, to see how to configure one.");
            default -> {
            }
        }
        System.out.println(body);
    }

    private static String readBody(HttpExecuteResponse response) throws IOException {
        Optional<? extends InputStream> body = response.responseBody();
        if (body.isEmpty()) {
            return "";
        }
        try (InputStream stream = body.get()) {
            return IoUtils.toUtf8String(stream);
        }
    }

}
