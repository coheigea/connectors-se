package org.talend.components.extension;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.talend.sdk.component.runtime.input.Input;
import org.talend.sdk.component.runtime.input.Mapper;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class PollingMapper implements Mapper, Serializable {
    private final PollingConfiguration pollingConfiguration;

    private final Mapper batchMapper;

    @Override
    public long assess() {
        return batchMapper.assess();
    }

    @Override
    public List<Mapper> split(long desiredSize) {
        return batchMapper.split(desiredSize);
    }

    @Override
    public void start() {
        batchMapper.start();
    }

    @Override
    public void stop() {
        batchMapper.stop();
    }

    @Override
    public Input create() {
        final Input input = batchMapper.create();
        return new PoolingInput(pollingConfiguration, input);
    }

    @Override
    public boolean isStream() {
        return true;
    }

    @Override
    public String plugin() {
        return batchMapper.plugin();
    }

    @Override
    public String rootName() {
        return batchMapper.rootName();
    }

    @Override
    public String name() {
        return batchMapper.name();
    }
}
