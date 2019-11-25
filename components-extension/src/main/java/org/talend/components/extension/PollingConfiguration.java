package org.talend.components.extension;

import org.talend.sdk.component.api.configuration.Option;

import java.io.Serializable;

public class PollingConfiguration implements Serializable {

    @Option
    private Integer delay;

}
