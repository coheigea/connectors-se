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
package org.talend.components.bigquery.input;

import com.google.cloud.bigquery.*;
import org.talend.components.bigquery.dataset.TableDataSet;
import org.talend.components.bigquery.datastore.BigQueryConnection;
import org.talend.components.bigquery.service.BigQueryService;
import org.talend.components.bigquery.service.I18nMessage;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Date;
import java.util.Iterator;

@Version(1)
@Icon(Icon.IconType.BIGQUERY)
// @Emitter(name = "BigQueryTableInput")
@Documentation("This component reads a table from BigQuery.")
public class BigQueryTableInput implements Serializable {

    protected final BigQueryConnection connection;

    protected final BigQueryService service;

    protected final I18nMessage i18n;

    protected final RecordBuilderFactory builderFactory;

    private TableDataSet dataSet;

    private transient Schema tableSchema;

    private transient Iterator<FieldValueList> queryResult;

    private transient boolean loaded = false;

    public BigQueryTableInput(@Option("configuration") BigQueryTableInputConfig configuration, final BigQueryService service,
            final I18nMessage i18n, final RecordBuilderFactory builderFactory) {
        this.connection = configuration.getDataStore();
        this.service = service;
        this.i18n = i18n;
        this.builderFactory = builderFactory;
        this.dataSet = configuration.getTableDataset();
    }

    @PostConstruct
    public void init() {

    }

    @Producer
    public Record next() {

        BigQuery bigQuery = BigQueryService.createClient(connection);

        if (!loaded) {
            try {
                TableId tableId = TableId.of(connection.getProjectName(), dataSet.getBqDataset(), dataSet.getTableName());
                Table table = bigQuery.getTable(tableId);
                tableSchema = table.getDefinition().getSchema();
                TableResult tableResult = bigQuery.listTableData(tableId, tableSchema);
                queryResult = tableResult.iterateAll().iterator();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                loaded = true;
            }
        }

        Record record = null;

        if (queryResult != null && queryResult.hasNext()) {
            FieldValueList fieldValueList = queryResult.next();

            Record.Builder rb = builderFactory.newRecordBuilder();

            for (Field f : tableSchema.getFields()) {
                String name = f.getName();
                FieldValue value = fieldValueList.get(name);
                LegacySQLTypeName type = f.getType();

                switch (type.name()) {
                case "BOOLEAN":
                    rb.withBoolean(name, value.getBooleanValue());
                    break;
                case "BYTES":
                    rb.withBytes(name, value.getBytesValue());
                    break;
                case "TIMESTAMP":
                    rb.withTimestamp(name, value.getTimestampValue());
                    break;
                case "DATETIME":
                    rb.withDateTime(name, new Date(value.getTimestampValue()));
                    break;
                case "FLOAT":
                    rb.withDouble(name, value.getDoubleValue());
                    break;
                case "INTEGER":
                    rb.withLong(name, value.getLongValue());
                    break;
                case "TIME":
                    rb.withLong(name, value.getLongValue());
                    break;
                default:
                    rb.withString(name, value.getStringValue());
                }

            }

            record = rb.build();
        }

        return record;
    }

}