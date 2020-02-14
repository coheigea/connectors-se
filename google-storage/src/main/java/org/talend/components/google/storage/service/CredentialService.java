/*
 * Copyright (C) 2006-2020 Talend Inc. - www.talend.com
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
package org.talend.components.google.storage.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import org.talend.components.google.storage.datastore.GSDataStore;
import org.talend.sdk.component.api.service.Service;

import com.google.api.services.storage.StorageScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CredentialService {

    /**
     * Build new Access Storage from credentials.
     * 
     * @return storage.
     */
    public Storage newStorage(GoogleCredentials credentials) {
        return StorageOptions.newBuilder().setCredentials(credentials).build().getService();
    }

    /**
     * get valable google access toekn.
     * 
     * @return google credential containing access token.
     */
    public GoogleCredentials getCredentials(GSDataStore ds) {
        try {
            String jsonCredentials = ds.getJsonCredentials();
            return GoogleCredentials.fromStream(new ByteArrayInputStream(jsonCredentials.getBytes(Charset.defaultCharset())))
                    .createScoped(StorageScopes.all());
        } catch (IOException e) {
            throw new RuntimeException("Exception when read service account, is:" + e.getMessage(), e);
        }
    }
}
