/*
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.talend.components.netsuite.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.components.netsuite.NetSuiteBaseTest;
import org.talend.components.netsuite.datastore.NetSuiteDataStore;
import org.talend.components.netsuite.datastore.NetSuiteDataStore.ApiVersion;
import org.talend.components.netsuite.datastore.NetSuiteDataStore.LoginType;
import org.talend.components.netsuite.runtime.client.NetSuiteException;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.maven.MavenDecrypter;
import org.talend.sdk.component.maven.Server;

@Slf4j
@WithComponents("org.talend.components.netsuite")
public class NetSuiteServiceTest extends NetSuiteBaseTest {

    @Test
    public void testConnectFailedMissingUserCredentials() {
        log.info("Integration test 'test failed missing user credentials' start ");
        NetSuiteDataStore dataStoreLocal = new NetSuiteDataStore();
        dataStoreLocal.setApiVersion(ApiVersion.V2018_2);

        // Missing endpoint
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));
        dataStoreLocal.setEndpoint(NETSUITE_ENDPOINT_URL);

        // Missing account
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));

        // Missing email
        dataStoreLocal.setAccount(NETSUITE_ACCOUNT);
        dataStoreLocal.setLoginType(LoginType.BASIC);
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));

        final MavenDecrypter decrypter = new MavenDecrypter();
        Server netsuiteCredentials = decrypter.find("netsuite");

        // Missing password
        dataStoreLocal.setEmail(netsuiteCredentials.getUsername());
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));

        // Missing roleId
        dataStoreLocal.setPassword(netsuiteCredentials.getPassword());
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));
    }

    @Test
    public void testConnectFailedMissingTokenBasedCredentials() {
        log.info("Integration test 'test failed missing token based credentials' start ");
        NetSuiteDataStore dataStoreLocal = new NetSuiteDataStore();

        // Missing endpoint
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));
        dataStoreLocal.setEndpoint("https://webservices.netsuite.com/services/NetSuitePort_2016_2");

        // Missing account
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));

        // Missing consumer key
        dataStoreLocal.setAccount(NETSUITE_ACCOUNT);
        dataStoreLocal.setLoginType(LoginType.TBA);
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));

        final MavenDecrypter decrypter = new MavenDecrypter();
        Server consumer = decrypter.find("netsuite.consumer");
        Server token = decrypter.find("netsuite.token");
        // Missing consumer secret
        dataStoreLocal.setConsumerKey(consumer.getUsername());
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));

        // Missing missing token id
        dataStoreLocal.setConsumerSecret(consumer.getPassword());
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));

        // Missing missing token secret
        dataStoreLocal.setTokenId(token.getUsername());
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));

        // Api version is different from endpoint.
        dataStoreLocal.setApiVersion(ApiVersion.V2018_2);
        dataStoreLocal.setTokenSecret(token.getPassword());
        Assertions.assertThrows(NetSuiteException.class, () -> service.connect(dataStoreLocal));
    }
}
