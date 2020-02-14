package org.talend.components.extension;

import org.talend.sdk.component.api.configuration.Option;

import java.io.Serializable;

public class PollingConfiguration implements Serializable {

    public final static int version = 1;

    @Option
    private Integer delay;

}
