/**
 * Copyright 2017 Yahoo Inc.
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
package com.yahoo.athenz.example.ztoken;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.PrivateKey;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.yahoo.athenz.auth.Principal;
import com.yahoo.athenz.auth.ServiceIdentityProvider;
import com.yahoo.athenz.auth.impl.SimpleServiceIdentityProvider;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.zts.RoleToken;
import com.yahoo.athenz.zts.ZTSClient;

public class HttpExampleClient {

    public HttpExampleClient() {
    }
    
    public static void main(String[] args) throws MalformedURLException, IOException {
        
        // parse our command line to retrieve required input
        
        CommandLine cmd = parseCommandLine(args);

        String domainName = cmd.getOptionValue("domain");
        String serviceName = cmd.getOptionValue("service");
        String privateKeyPath = cmd.getOptionValue("pkey");
        String keyId = cmd.getOptionValue("keyid");
        String url = cmd.getOptionValue("url");
        String ztsUrl = cmd.getOptionValue("ztsurl");
        String providerDomain = cmd.getOptionValue("provider");
        String providerRole = cmd.getOptionValue("role");
        
        // we need to generate our principal credentials (ntoken). In
        // addition to the domain and service names, we need the
        // the service's private key and the key identifier - the
        // service with the corresponding public key must already be
        // registered in ZMS
        
        PrivateKey privateKey = Crypto.loadPrivateKey(new File(privateKeyPath));
        ServiceIdentityProvider identityProvider = new SimpleServiceIdentityProvider(domainName,
                serviceName, privateKey, keyId);
        
        // now we need to retrieve a role token (ztoken) for accessing
        // the provider Athenz enabled service
        
        RoleToken roleToken = null;
        try (ZTSClient ztsClient = new ZTSClient(ztsUrl, domainName, serviceName,
                identityProvider)) {
            roleToken = ztsClient.getRoleToken(providerDomain, providerRole);
        }
        
        if (roleToken == null) {
            System.out.println("Unable to retrieve role token for: " + providerRole
                    + " in domain: " + providerDomain);
            System.exit(1);
        }
        
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        
        // set our Athenz credentials. Th ZTSClient provides the header
        // name that we must use for authorization token while the role
        // token itself provides the token string (ztoken).
        
        con.setRequestProperty(ZTSClient.getHeader(), roleToken.getToken());
        
        // now process our request
        
        int responseCode = con.getResponseCode();
        switch (responseCode) {
        case HttpURLConnection.HTTP_FORBIDDEN:
            System.out.println("Request was forbidden - not authorized");
            break;
        case HttpURLConnection.HTTP_OK:
            System.out.println("Successful response: ");
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println(inputLine);
                }
            }
            break;
        default:
            System.out.println("Request failed - response status code: " + responseCode);
        }
    }
    
    private static CommandLine parseCommandLine(String[] args) {
        
        Options options = new Options();
        
        Option domain = new Option("d", "domain", true, "domain name");
        domain.setRequired(true);
        options.addOption(domain);
        
        Option service = new Option("s", "service", true, "service name");
        service.setRequired(true);
        options.addOption(service);
        
        Option privateKey = new Option("p", "pkey", true, "private key path");
        privateKey.setRequired(true);
        options.addOption(privateKey);
        
        Option keyId = new Option("k", "keyid", true, "key identifier");
        keyId.setRequired(true);
        options.addOption(keyId);
        
        Option url = new Option("u", "url", true, "request url");
        url.setRequired(true);
        options.addOption(url);
        
        Option ztsUrl = new Option("z", "ztsurl", true, "ZTS Server url");
        ztsUrl.setRequired(true);
        options.addOption(ztsUrl);
        
        Option providerDomain = new Option("z", "provider", true, "Provider domain name");
        providerDomain.setRequired(true);
        options.addOption(providerDomain);
        
        Option providerRole = new Option("r", "role", true, "Provider role name");
        providerRole.setRequired(true);
        options.addOption(providerRole);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;
        
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("http-example-client", options);
            System.exit(1);
        }
        
        return cmd;
    }
}
